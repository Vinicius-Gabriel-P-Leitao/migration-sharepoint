/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer;

import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.ConflictException;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MySqlMigrationWriter {

    @Value("${writer.batch-size:500}")
    private int batchSize;

    public void write(String connectionString, String tableName, List<Map<String, Object>> rows) {
        if (!tableName.matches("[a-zA-Z0-9_]+")) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Nome de tabela inválido: " + tableName);
        }

        if (rows.isEmpty()) {
            log.info("Nenhum dado para migrar na tabela '{}'", tableName);
            return;
        }

        try (Connection conn = DriverManager.getConnection(connectionString)) {
            String catalog = conn.getCatalog();

            assertTableExists(conn, catalog, tableName);

            List<String> tableColumns = fetchTableColumns(conn, catalog, tableName);
            List<String> insertColumns = rows.get(0).keySet().stream()
                    .filter(tableColumns::contains)
                    .collect(Collectors.toList());

            if (insertColumns.isEmpty()) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST,
                        "Nenhuma coluna mapeada corresponde às colunas da tabela '%s'".formatted(tableName));
            }

            batchInsert(conn, tableName, insertColumns, rows);
            log.info("{} linhas inseridas na tabela '{}'", rows.size(), tableName);

        } catch (SQLException e) {
            throw new InfrastructureException(ErrorCode.DB_CONNECTION_ERROR,
                    "Erro ao conectar ao banco de dados: " + e.getMessage());
        }
    }

    private void assertTableExists(Connection conn, String catalog, String tableName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            stmt.setString(1, catalog);
            stmt.setString(2, tableName);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            if (rs.getInt(1) == 0) {
                throw new NotFoundException(ErrorCode.TABLE_NOT_FOUND,
                        "Tabela '%s' não encontrada no banco de destino".formatted(tableName));
            }
        }
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

    private void batchInsert(Connection conn, String tableName, List<String> columns,
            List<Map<String, Object>> rows) throws SQLException {
        String colList = columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = "INSERT INTO `%s` (%s) VALUES (%s)".formatted(tableName, colList, placeholders);

        conn.setAutoCommit(false);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int count = 0;
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    stmt.setObject(i + 1, row.get(columns.get(i)));
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

            conn.commit();
        } catch (SQLIntegrityConstraintViolationException e) {
            conn.rollback();
            throw new ConflictException(ErrorCode.MIGRATION_CONFLICT,
                    "Conflito de integridade ao inserir dados: " + e.getMessage());
        } catch (SQLException e) {
            conn.rollback();
            throw new InfrastructureException(ErrorCode.DB_CONNECTION_ERROR,
                    "Erro ao executar inserção: " + e.getMessage());
        }
    }
}
