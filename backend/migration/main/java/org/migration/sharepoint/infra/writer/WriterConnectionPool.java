/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Mantém um pool HikariCP por connectionKey, reutilizado entre execuções do
 * mesmo job. A URL real é resolvida via {@link ConnectionRegistry} — nunca fica
 * armazenada aqui diretamente.
 *
 * <p>
 * Se a inicialização do pool falhar, a key é marcada como inválida e todas as
 * tentativas subsequentes falham imediatamente sem tentar reconectar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriterConnectionPool implements DisposableBean {

    private static final int MAX_POOL_SIZE = 5;
    private static final long CONNECTION_TIMEOUT_MS = 30_000;
    private static final long IDLE_TIMEOUT_MS = 600_000;
    private static final long MAX_LIFETIME_MS = 1_800_000;

    private final ConnectionRegistry connectionRegistry;
    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Set<String> failedKeys = ConcurrentHashMap.newKeySet();

    public Connection getConnection(String connectionKey) throws SQLException {
        if (failedKeys.contains(connectionKey)) {
            throw new InfrastructureException(
                    ErrorCode.DB_CONNECTION_ERROR,
                    "Pool para key='%s' falhou na inicialização anterior — verifique a URL da conexão e reinicie o servidor ou re-registre a conexão via DELETE + POST /v1/connections"
                            .formatted(connectionKey));
        }

        String jdbcUrl = connectionRegistry.resolveUrl(connectionKey);

        try {
            HikariDataSource ds = pools.computeIfAbsent(connectionKey, key -> createPool(key, jdbcUrl));
            return ds.getConnection();
        } catch (RuntimeException poolException) {
            failedKeys.add(connectionKey);
            log.error("Falha ao inicializar pool para key='{}': {}", connectionKey, poolException.getMessage());

            throw new InfrastructureException(
                    ErrorCode.DB_CONNECTION_ERROR,
                    "Falha ao conectar ao banco de dados para key='%s': %s"
                            .formatted(connectionKey, poolException.getMessage()));
        }
    }

    private HikariDataSource createPool(String connectionKey, String jdbcUrl) {
        log.info("Criando pool de conexões para key={}", connectionKey);
        ParsedUrl parsed = parseCredentials(jdbcUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(parsed.url());

        if (parsed.username() != null) config.setUsername(parsed.username());
        if (parsed.password() != null) config.setPassword(parsed.password());

        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setIdleTimeout(IDLE_TIMEOUT_MS);
        config.setMaxLifetime(MAX_LIFETIME_MS);
        config.setPoolName("migration-writer-%s".formatted(connectionKey));

        return new HikariDataSource(config);
    }

    /**
     * Extrai user/password da JDBC URL e os remove dela, para que o HikariCP os
     * receba via setUsername/setPassword. Suporta dois formatos:
     *
     * <ul>
     * <li>Query params: {@code jdbc:mysql://host/db?user=u&password=p&other=x}
     * <li>Authority: {@code jdbc:mysql://u:p@host/db?other=x}
     * </ul>
     */
    private ParsedUrl parseCredentials(String jdbcUrl) {
        int queryStart = jdbcUrl.indexOf('?');

        if (queryStart >= 0) {
            String base = jdbcUrl.substring(0, queryStart);
            String query = jdbcUrl.substring(queryStart + 1);
            Map<String, String> remaining = new LinkedHashMap<>();

            String username = null;
            String password = null;

            for (String param : query.split("&")) {
                int eq = param.indexOf('=');
                if (eq < 0) {
                    remaining.put(param, "");
                    continue;
                }

                String key = param.substring(0, eq);
                String value = param.substring(eq + 1);

                switch (key) {
                    case "user" -> username = value;
                    case "password" -> password = value;
                    default -> remaining.put(key, value);
                }
            }

            if (username != null || password != null) {
                String newQuery = remaining.entrySet().stream()
                        .map(entry -> entry.getValue().isEmpty() ? entry.getKey() : "%s=%s".formatted(entry.getKey(), entry.getValue()))
                        .reduce("%s&%s"::formatted)
                        .orElse("");

                String cleanUrl = newQuery.isEmpty() ? base : "%s?%s".formatted(base, newQuery);
                return new ParsedUrl(cleanUrl, username, password);
            }
        }

        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd >= 0) {
            String afterScheme = jdbcUrl.substring(schemeEnd + 3);
            int atSign = afterScheme.indexOf('@');

            if (atSign >= 0) {
                String userInfo = afterScheme.substring(0, atSign);
                String rest = afterScheme.substring(atSign + 1);
                String scheme = jdbcUrl.substring(0, schemeEnd + 3);

                int colon = userInfo.indexOf(':');

                String username = colon >= 0 ? userInfo.substring(0, colon) : userInfo;
                String password = colon >= 0 ? userInfo.substring(colon + 1) : null;

                return new ParsedUrl(scheme + rest, username.isEmpty() ? null : username, password);
            }
        }

        return new ParsedUrl(jdbcUrl, null, null);
    }

    private record ParsedUrl(String url, String username, String password) {}

    @Override
    public void destroy() {
        log.info("Fechando {} pool(s) de conexões do writer", pools.size());
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
