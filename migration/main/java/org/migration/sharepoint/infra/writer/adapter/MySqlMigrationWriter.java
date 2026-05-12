/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.ConflictException;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.WriterConnectionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlMigrationWriter implements MigrationWriter {

    @Value("${writer.batch-size:500}")
    private int batchSize;

    private final WriterConnectionPool connectionPool;

    @Override
    public boolean supports(TargetDb targetDb) {
        return targetDb == TargetDb.MYSQL;
    }

    @Override
    public void write(String connectionString, String targetName, List<Map<String, Object>> rows) {
        validateTableName(targetName);
        validateNoNestedPaths(rows);

        if (rows.isEmpty()) {
            log.info("Nenhum dado para migrar na tabela '{}'", targetName);
            return;
        }

        try (Connection conn = connectionPool.getConnection(connectionString)) {
            String catalog = conn.getCatalog();

            List<String> requestedColumns = new ArrayList<>(rows.getFirst().keySet());
            createTableIfAbsent(conn, catalog, targetName, requestedColumns, rows.getFirst());

            List<String> tableColumns = fetchTableColumns(conn, catalog, targetName);
            List<String> insertColumns = requestedColumns.stream().filter(tableColumns::contains).collect(Collectors.toList());

            if (insertColumns.isEmpty()) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST,
                        "Nenhuma coluna mapeada corresponde às colunas da tabela '%s'".formatted(targetName));
            }

            replaceAll(conn, targetName, insertColumns, rows);
            log.info("{} linhas migradas para a tabela '{}' (full replace)", rows.size(), targetName);

        } catch (SQLException sqlException) {
            throw new InfrastructureException(ErrorCode.DB_CONNECTION_ERROR, "Erro de conexão com o banco de dados: " + sqlException.getMessage());
        }
    }

    private void replaceAll(Connection conn, String tableName, List<String> columns, List<Map<String, Object>> rows) throws SQLException {
        conn.setAutoCommit(false);
        try {
            // enquoteIdentifier usa o quote char do próprio driver (` no MySQL, " no PostgreSQL).
            // Internamente escapa qualquer ocorrência do quote char dentro do nome,
            // tornando inofensivo qualquer caracter que tenha passado pela validação de regex.
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM " + stmt.enquoteIdentifier(tableName, true));
            }

            batchInsert(conn, tableName, columns, rows);
            conn.commit();

        } catch (SQLException sqlException) {
            conn.rollback();
            throw sqlException;
        }
    }

    private void batchInsert(Connection conn, String tableName, List<String> columns, List<Map<String, Object>> rows) throws SQLException {
        try (Statement helper = conn.createStatement()) {
            String quotedTable = helper.enquoteIdentifier(tableName, true);

            String colList = columns.stream().map(it -> {
                try {
                    return helper.enquoteIdentifier(it, true);
                } catch (SQLException sqlException) {
                    throw new RuntimeException(sqlException);
                }
            }).collect(Collectors.joining(", "));

            String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(quotedTable, colList, placeholders);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int count = 0;
                for (Map<String, Object> row : rows) {
                    for (int it = 0; it < columns.size(); it++) {
                        stmt.setObject(it + 1, row.get(columns.get(it)));
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
                throw new ConflictException(ErrorCode.MIGRATION_CONFLICT,
                        "Conflito de integridade ao inserir dados: %s".formatted(constraintViolation.getMessage()));
            }
        }
    }

    private void createTableIfAbsent(Connection conn, String catalog, String tableName,
            List<String> columns, Map<String, Object> sampleRow) throws SQLException {
        try (PreparedStatement check = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            check.setString(1, catalog);
            check.setString(2, tableName);
            ResultSet rs = check.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) return;
        }

        try (Statement stmt = conn.createStatement()) {
            String quotedTable = stmt.enquoteIdentifier(tableName, true);
            String columnDefs = columns.stream().map(col -> {
                try {
                    return stmt.enquoteIdentifier(col, true) + " " + inferSqlType(sampleRow.get(col));
                } catch (SQLException sqlException) {
                    throw new RuntimeException(sqlException);
                }
            }).collect(Collectors.joining(", "));

            stmt.execute("CREATE TABLE IF NOT EXISTS %s (%s)".formatted(quotedTable, columnDefs));
            log.info("Tabela '{}' criada automaticamente", tableName);
        }
    }

    private String inferSqlType(Object value) {
        if (value instanceof Integer || value instanceof Long) return "BIGINT";
        if (value instanceof Double || value instanceof Float) return "DOUBLE";
        if (value instanceof Boolean) return "TINYINT(1)";
        return "TEXT";
    }

    private List<String> fetchTableColumns(Connection conn, String catalog, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
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
        rows.getFirst().keySet().stream().filter(it -> it.contains(".")).findFirst().ifPresent(it -> {
            throw new BadRequestException(ErrorCode.BAD_REQUEST,
                    "Nome de coluna inválido para SQL: '%s' — dot-notation é exclusivo do adapter MongoDB".formatted(it));
        });
    }
}
