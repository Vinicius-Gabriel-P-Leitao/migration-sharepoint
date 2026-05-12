/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.connection;

import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.ConflictException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Registry em memória de connection strings para os bancos de destino.
 *
 * <p>Na startup, carrega automaticamente pares de variáveis de ambiente:
 *
 * <pre>
 *   CONN_URL_{KEY}  = jdbc:mysql://host:3306/db?user=u&amp;password=p
 *   CONN_NAME_{KEY} = MySQL Produção
 * </pre>
 *
 * <p>Conexões também podem ser registradas em runtime via API (POST /v1/connections). Os jobs
 * armazenam apenas a chave ({@code connectionKey}) no SQLite — nunca a URL.
 */
@Slf4j
@Component
public class ConnectionRegistry {

  private static final String URL_PREFIX = "CONN_URL_";
  private static final String NAME_PREFIX = "CONN_NAME_";

  private record Entry(String name, String url) {}

  private final ConcurrentHashMap<String, Entry> registry = new ConcurrentHashMap<>();

  @PostConstruct
  void loadFromEnv() {
    System.getenv()
        .forEach(
            (key, value) -> {
              if (key.startsWith(URL_PREFIX)) {
                String suffix = key.substring(URL_PREFIX.length());
                String name = System.getenv(NAME_PREFIX + suffix);

                if (name != null && !name.isBlank()) {
                  registry.put(suffix, new Entry(name, value));
                  log.info("Conexão carregada do ambiente: key={} name={}", suffix, name);
                } else {
                  log.warn(
                      "{}{} definida sem {}{} correspondente — ignorada",
                      URL_PREFIX,
                      suffix,
                      NAME_PREFIX,
                      suffix);
                }
              }
            });
    log.info("{} conexão(ões) carregada(s) do ambiente", registry.size());
  }

  public void register(String key, String name, String url) {
    if (registry.containsKey(key)) {
      throw new ConflictException(
          ErrorCode.CONNECTION_KEY_CONFLICT,
          "Chave '%s' já registrada — use DELETE /v1/connections/%s antes de re-registrar"
              .formatted(key, key));
    }

    registry.put(key, new Entry(name, url));
    log.info("Conexão registrada via API: key={} name={}", key, name);
  }

  public String resolveUrl(String key) {
    Entry entry = registry.get(key);
    if (entry == null) {
      throw new NotFoundException(
          ErrorCode.CONNECTION_NOT_FOUND,
          "Conexão '%s' não encontrada — defina CONN_URL_%s + CONN_NAME_%s ou registre via POST /v1/connections"
              .formatted(key, key, key));
    }
    return entry.url();
  }

  public List<ConnectionSummary> list() {
    return registry.entrySet().stream()
        .map(entry -> new ConnectionSummary(entry.getKey(), entry.getValue().name()))
        .sorted(Comparator.comparing(ConnectionSummary::key))
        .toList();
  }

  public void remove(String key) {
    if (registry.remove(key) == null) {
      throw new NotFoundException(
          ErrorCode.CONNECTION_NOT_FOUND, "Conexão '%s' não encontrada".formatted(key));
    }

    log.info("Conexão removida: key={}", key);
  }

  public record ConnectionSummary(String key, String name) {}
}
