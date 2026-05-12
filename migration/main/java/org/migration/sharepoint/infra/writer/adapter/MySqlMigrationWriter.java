/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer.adapter;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.ConflictException;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.WriterConnectionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlMigrationWriter implements MigrationWriter {

  private static final List<String> MYSQL_NATIVE_TYPES =
      List.of(
          "TINYINT",
          "SMALLINT",
          "INT",
          "BIGINT",
          "FLOAT",
          "DOUBLE",
          "DECIMAL",
          "TINYINT(1)",
          "VARCHAR",
          "TEXT",
          "MEDIUMTEXT",
          "LONGTEXT",
          "DATE",
          "DATETIME",
          "TIMESTAMP");

  private static final Map<ColumnType, String> MYSQL_CANONICAL_MAP =
      Map.of(
          ColumnType.TEXT,
          "TEXT",
          ColumnType.NUMBER,
          "BIGINT",
          ColumnType.DECIMAL,
          "DOUBLE",
          ColumnType.BOOLEAN,
          "TINYINT(1)",
          ColumnType.DATE,
          "DATE",
          ColumnType.DATETIME,
          "DATETIME");

  @Value("${writer.batch-size:500}")
  private int batchSize;

  private final WriterConnectionPool connectionPool;

  @Override
  public boolean supports(TargetDb targetDb) {
    return targetDb == TargetDb.MYSQL;
  }

  @Override
  public List<String> nativeTypes() {
    return MYSQL_NATIVE_TYPES;
  }

  @Override
  public Map<ColumnType, String> canonicalMapping() {
    return MYSQL_CANONICAL_MAP;
  }

  @Override
  public void write(
      String connectionKey,
      String targetName,
      List<Map<String, Object>> rows,
      Map<String, FieldMapping> columnTypes) {
    validateTableName(targetName);
    validateNoNestedPaths(rows);

    if (rows.isEmpty()) {
      log.info("Nenhum dado para migrar na tabela '{}'", targetName);
      return;
    }

    try (Connection conn = connectionPool.getConnection(connectionKey)) {
      String catalog = conn.getCatalog();

      List<String> requestedColumns = new ArrayList<>(rows.getFirst().keySet());
      createTableIfAbsent(conn, catalog, targetName, requestedColumns, columnTypes);

      List<String> tableColumns = fetchTableColumns(conn, catalog, targetName);
      List<String> insertColumns =
          requestedColumns.stream().filter(tableColumns::contains).collect(Collectors.toList());

      if (insertColumns.isEmpty()) {
        throw new BadRequestException(
            ErrorCode.BAD_REQUEST,
            "Nenhuma coluna mapeada corresponde às colunas da tabela '%s'".formatted(targetName));
      }

      replaceAll(conn, targetName, insertColumns, rows, columnTypes);
      log.info("{} linhas migradas para a tabela '{}' (full replace)", rows.size(), targetName);

    } catch (SQLException sqlException) {
      throw new InfrastructureException(
          ErrorCode.DB_CONNECTION_ERROR,
          "Erro de conexão com o banco de dados: %s".formatted(sqlException.getMessage()));
    }
  }

  private void createTableIfAbsent(
      Connection conn,
      String catalog,
      String tableName,
      List<String> columns,
      Map<String, FieldMapping> columnTypes)
      throws SQLException {
    try (PreparedStatement check =
        conn.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
      check.setString(1, catalog);
      check.setString(2, tableName);
      ResultSet rs = check.executeQuery();
      rs.next();
      if (rs.getInt(1) > 0) return;
    }

    try (Statement stmt = conn.createStatement()) {
      String quotedTable = stmt.enquoteIdentifier(tableName, true);
      String columnDefs =
          columns.stream()
              .map(
                  col -> {
                    try {
                      String sqlType =
                          columnTypes.containsKey(col)
                              ? resolveType(col, columnTypes.get(col))
                              : "TEXT";
                      return stmt.enquoteIdentifier(col, true) + " " + sqlType;
                    } catch (SQLException sqlException) {
                      throw new RuntimeException(sqlException);
                    }
                  })
              .collect(Collectors.joining(", "));

      stmt.execute("CREATE TABLE IF NOT EXISTS %s (%s)".formatted(quotedTable, columnDefs));
      log.info("Tabela '{}' criada automaticamente", tableName);
    }
  }

  private String resolveType(String column, FieldMapping mapping) {
    if (mapping == null) {
      throw new BadRequestException(
          ErrorCode.BAD_REQUEST,
          "Mapeamento ausente para a coluna '%s': informe 'type' ou 'nativeType'"
              .formatted(column));
    }

    if (mapping.nativeType() != null && !mapping.nativeType().isBlank()) {
      String inputBase = mapping.nativeType().split("\\(")[0].toUpperCase().trim();
      boolean valid =
          MYSQL_NATIVE_TYPES.stream()
              .anyMatch(supported -> supported.split("\\(")[0].equalsIgnoreCase(inputBase));
      if (!valid) {
        throw new BadRequestException(
            ErrorCode.BAD_REQUEST,
            "Tipo nativo inválido para MySQL na coluna '%s': '%s'. Tipos suportados: %s"
                .formatted(column, mapping.nativeType(), MYSQL_NATIVE_TYPES));
      }
      return mapping.nativeType();
    }

    if (mapping.type() != null) {
      String resolved = MYSQL_CANONICAL_MAP.get(mapping.type());
      if (resolved == null) {
        throw new BadRequestException(
            ErrorCode.BAD_REQUEST,
            "Tipo canônico '%s' não mapeado para MySQL na coluna '%s'"
                .formatted(mapping.type(), column));
      }
      return resolved;
    }

    throw new BadRequestException(
        ErrorCode.BAD_REQUEST,
        "Coluna '%s' sem tipo definido: informe 'type' (canônico) ou 'nativeType' (nativo MySQL)"
            .formatted(column));
  }

  private void replaceAll(
      Connection conn,
      String tableName,
      List<String> columns,
      List<Map<String, Object>> rows,
      Map<String, FieldMapping> columnTypes)
      throws SQLException {
    conn.setAutoCommit(false);
    try {
      // enquoteIdentifier usa o quote char do próprio driver (` no MySQL).
      // Internamente escapa qualquer ocorrência do quote char dentro do nome.
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("DELETE FROM " + stmt.enquoteIdentifier(tableName, true));
      }

      batchInsert(conn, tableName, columns, rows, columnTypes);
      conn.commit();

    } catch (SQLException sqlException) {
      conn.rollback();
      throw sqlException;
    }
  }

  private void batchInsert(
      Connection conn,
      String tableName,
      List<String> columns,
      List<Map<String, Object>> rows,
      Map<String, FieldMapping> columnTypes)
      throws SQLException {
    try (Statement helper = conn.createStatement()) {
      String quotedTable = helper.enquoteIdentifier(tableName, true);

      String colList =
          columns.stream()
              .map(
                  col -> {
                    try {
                      return helper.enquoteIdentifier(col, true);
                    } catch (SQLException sqlException) {
                      throw new RuntimeException(sqlException);
                    }
                  })
              .collect(Collectors.joining(", "));

      String placeholders = columns.stream().map(col -> "?").collect(Collectors.joining(", "));
      String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(quotedTable, colList, placeholders);

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        int count = 0;
        for (Map<String, Object> row : rows) {
          for (int it = 0; it < columns.size(); it++) {
            String col = columns.get(it);
            Object value = convertValue(row.get(col), columnTypes.get(col));
            stmt.setObject(it + 1, value);
          }

          stmt.addBatch();
          count++;

          if (count % batchSize == 0) {
            stmt.executeBatch();
            stmt.clearBatch();
            log.debug("Batch parcial executado: {} linhas", count);
          }
        }

        if (count % batchSize != 0) {
          stmt.executeBatch();
        }
      } catch (SQLIntegrityConstraintViolationException constraintViolation) {
        throw new ConflictException(
            ErrorCode.MIGRATION_CONFLICT,
            "Conflito de integridade ao inserir dados: %s"
                .formatted(constraintViolation.getMessage()));
      }
    }
  }

  private Object convertValue(Object value, FieldMapping mapping) {
    if (value == null || mapping == null) return value;

    String raw = value instanceof String s ? s : null;
    if (raw == null) return value;

    ColumnType type = mapping.type();
    String nativeType = mapping.nativeType() != null ? mapping.nativeType().toUpperCase() : null;

    boolean isDatetime =
        type == ColumnType.DATETIME
            || (nativeType != null
                && (nativeType.startsWith("DATETIME") || nativeType.startsWith("TIMESTAMP")));
    boolean isDate =
        !isDatetime
            && (type == ColumnType.DATE || (nativeType != null && nativeType.startsWith("DATE")));

    if (isDatetime) {
      try {
        return LocalDateTime.ofInstant(Instant.parse(raw), ZoneOffset.UTC);
      } catch (Exception ignored) {
        return value;
      }
    }
    if (isDate) {
      try {
        return LocalDateTime.ofInstant(Instant.parse(raw), ZoneOffset.UTC).toLocalDate();
      } catch (Exception ignored) {
        return value;
      }
    }
    return value;
  }

  private List<String> fetchTableColumns(Connection conn, String catalog, String tableName)
      throws SQLException {
    List<String> columns = new ArrayList<>();
    try (PreparedStatement stmt =
        conn.prepareStatement(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
      stmt.setString(1, catalog);
      stmt.setString(2, tableName);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        columns.add(rs.getString("COLUMN_NAME"));
      }
    }
    return columns;
  }

  private void validateTableName(String tableName) {
    if (!tableName.matches("[a-zA-Z0-9_]+")) {
      throw new BadRequestException(ErrorCode.BAD_REQUEST, "Nome de tabela inválido: " + tableName);
    }
  }

  private void validateNoNestedPaths(List<Map<String, Object>> rows) {
    if (rows.isEmpty()) return;
    rows.getFirst().keySet().stream()
        .filter(it -> it.contains("."))
        .findFirst()
        .ifPresent(
            it -> {
              throw new BadRequestException(
                  ErrorCode.BAD_REQUEST,
                  "Nome de coluna inválido para SQL: '%s' — dot-notation é exclusivo do adapter MongoDB"
                      .formatted(it));
            });
  }
}
