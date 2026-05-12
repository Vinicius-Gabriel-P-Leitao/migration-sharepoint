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
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Mantém um pool HikariCP por connectionKey, reutilizado entre execuções do mesmo job. A URL real é
 * resolvida via {@link ConnectionRegistry} — nunca fica armazenada aqui diretamente.
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

  public Connection getConnection(String connectionKey) throws SQLException {
    String jdbcUrl = connectionRegistry.resolveUrl(connectionKey);
    HikariDataSource ds = pools.computeIfAbsent(connectionKey, k -> createPool(k, jdbcUrl));
    return ds.getConnection();
  }

  private HikariDataSource createPool(String connectionKey, String jdbcUrl) {
    log.info("Criando pool de conexões para key={}", connectionKey);
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setMaximumPoolSize(MAX_POOL_SIZE);
    config.setMinimumIdle(1);
    config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
    config.setIdleTimeout(IDLE_TIMEOUT_MS);
    config.setMaxLifetime(MAX_LIFETIME_MS);
    config.setPoolName("migration-writer-" + connectionKey);
    return new HikariDataSource(config);
  }

  @Override
  public void destroy() {
    log.info("Fechando {} pool(s) de conexões do writer", pools.size());
    pools.values().forEach(HikariDataSource::close);
    pools.clear();
  }
}
