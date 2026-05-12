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
import org.migration.sharepoint.infra.writer.NativeTypeDefinition;
import org.migration.sharepoint.infra.writer.NativeTypeDefinition.ParamSpec;
import org.migration.sharepoint.infra.writer.WriterConnectionPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlMigrationWriter implements MigrationWriter {

    private static final List<NativeTypeDefinition> MYSQL_TYPE_DEFS = List.of(
            new NativeTypeDefinition("TINYINT", List.of()),
            new NativeTypeDefinition("SMALLINT", List.of()),
            new NativeTypeDefinition("INT", List.of()),
            new NativeTypeDefinition("BIGINT", List.of()),
            new NativeTypeDefinition("FLOAT", List.of()),
            new NativeTypeDefinition("DOUBLE", List.of()),
            new NativeTypeDefinition("DECIMAL", List.of(new ParamSpec("M", 1, 65), new ParamSpec("D", 0, 30))),
            new NativeTypeDefinition("TINYINT(1)", List.of()),
            new NativeTypeDefinition("CHAR", List.of(new ParamSpec("N", 1, 255))),
            new NativeTypeDefinition("VARCHAR", List.of(new ParamSpec("N", 1, 65535))),
            new NativeTypeDefinition("TEXT", List.of()),
            new NativeTypeDefinition("MEDIUMTEXT", List.of()),
            new NativeTypeDefinition("LONGTEXT", List.of()),
            new NativeTypeDefinition("DATE", List.of()),
            new NativeTypeDefinition("DATETIME", List.of()),
            new NativeTypeDefinition("TIMESTAMP", List.of()));

    private static final Map<ColumnType, String> MYSQL_CANONICAL_MAP = Map.of(
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

    // MySQL vendor error codes
    private static final int MYSQL_ERR_NULL_VIOLATION = 1048;

    @Value("${writer.batch-size:500}")
    private int batchSize;

    private final WriterConnectionPool connectionPool;

    @Override
    public boolean supports(TargetDb targetDb) {
        return targetDb == TargetDb.MYSQL;
    }

    @Override
    public List<NativeTypeDefinition> typeDefinitions() {
        return MYSQL_TYPE_DEFS;
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

            StringBuilder columnDefs = new StringBuilder();
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                if (columnIndex > 0) columnDefs.append(", ");
                String columnName = columns.get(columnIndex);
                String sqlType = columnTypes.containsKey(columnName)
                        ? resolveType(columnName, columnTypes.get(columnName))
                        : "TEXT";
                columnDefs
                        .append(stmt.enquoteIdentifier(columnName, true))
                        .append(" ")
                        .append(sqlType);
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS %s (%s)".formatted(quotedTable, columnDefs));
            log.info("Tabela '{}' criada automaticamente", tableName);
        }
    }

    private String resolveType(String column, FieldMapping mapping) {
        if (mapping == null) {
            throw new BadRequestException(
                    ErrorCode.BAD_REQUEST,
                    "Mapeamento ausente para a coluna '%s': informe 'type' ou 'nativeType'".formatted(column));
        }

        if (mapping.nativeType() != null && !mapping.nativeType().isBlank()) {
            return validateAndReturnNative(column, mapping.nativeType());
        }

        if (mapping.type() != null) {
            String resolved = MYSQL_CANONICAL_MAP.get(mapping.type());
            if (resolved == null) {
                throw new BadRequestException(
                        ErrorCode.BAD_REQUEST,
                        "Tipo canônico '%s' não mapeado para MySQL na coluna '%s'".formatted(mapping.type(), column));
            }
            return resolved;
        }

        throw new BadRequestException(
                ErrorCode.BAD_REQUEST,
                "Coluna '%s' sem tipo definido: informe 'type' (canônico) ou 'nativeType' (nativo MySQL)"
                        .formatted(column));
    }

    private String validateAndReturnNative(String column, String nativeType) {
        String upper = nativeType.toUpperCase().trim();
        String base = upper.split("\\(")[0].trim();

        // Exact match (handles literals como TINYINT(1))
        if (MYSQL_TYPE_DEFS.stream().anyMatch(typeDef -> typeDef.name().equalsIgnoreCase(upper))) {
            return nativeType;
        }

        // Base-name match para tipos parametrizados
        NativeTypeDefinition matchedDef = MYSQL_TYPE_DEFS.stream()
                .filter(typeDef -> typeDef.name().split("\\(")[0].equalsIgnoreCase(base)
                        && !typeDef.params().isEmpty())
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        ErrorCode.BAD_REQUEST,
                        "Tipo nativo inválido para MySQL na coluna '%s': '%s'. Tipos disponíveis: %s"
                                .formatted(
                                        column,
                                        nativeType,
                                        MYSQL_TYPE_DEFS.stream()
                                                .map(NativeTypeDefinition::name)
                                                .toList())));

        validateParams(column, upper, matchedDef);
        return nativeType;
    }

    private void validateParams(String column, String upperType, NativeTypeDefinition matchedDef) {
        String innerParams = upperType.contains("(") ? upperType.replaceAll("^[A-Z]+\\((.+)\\)$", "$1") : "";

        if (innerParams.isBlank()) {
            String expectedRanges = matchedDef.params().stream()
                    .map(paramSpec -> "%s ∈ [%d, %d]".formatted(paramSpec.label(), paramSpec.min(), paramSpec.max()))
                    .collect(Collectors.joining(", "));
            throw new BadRequestException(
                    ErrorCode.BAD_REQUEST,
                    "Tipo '%s' na coluna '%s' requer parâmetros: (%s)"
                            .formatted(matchedDef.name(), column, expectedRanges));
        }

        String[] rawParams = innerParams.split(",");
        if (rawParams.length != matchedDef.params().size()) {
            throw new BadRequestException(
                    ErrorCode.BAD_REQUEST,
                    "Tipo '%s' na coluna '%s': esperado %d parâmetro(s), recebido %d"
                            .formatted(
                                    matchedDef.name(),
                                    column,
                                    matchedDef.params().size(),
                                    rawParams.length));
        }

        for (int paramIndex = 0; paramIndex < matchedDef.params().size(); paramIndex++) {
            NativeTypeDefinition.ParamSpec paramSpec = matchedDef.params().get(paramIndex);
            String rawParam = rawParams[paramIndex].trim();
            int parsedValue;
            try {
                parsedValue = Integer.parseInt(rawParam);
            } catch (NumberFormatException numberFormatException) {
                throw new BadRequestException(
                        ErrorCode.BAD_REQUEST,
                        "Parâmetro '%s' do tipo '%s' na coluna '%s' deve ser inteiro, recebido: '%s'"
                                .formatted(paramSpec.label(), matchedDef.name(), column, rawParam));
            }
            if (parsedValue < paramSpec.min() || parsedValue > paramSpec.max()) {
                throw new BadRequestException(
                        ErrorCode.BAD_REQUEST,
                        "Parâmetro '%s' do tipo '%s' na coluna '%s': %d fora do intervalo [%d, %d]"
                                .formatted(
                                        paramSpec.label(),
                                        matchedDef.name(),
                                        column,
                                        parsedValue,
                                        paramSpec.min(),
                                        paramSpec.max()));
            }
        }
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
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM %s".formatted(stmt.enquoteIdentifier(tableName, true)));
            }
            batchInsert(conn, tableName, columns, rows, columnTypes);
            conn.commit();
        } catch (SQLException sqlException) {
            try {
                conn.rollback();
            } catch (SQLException rollbackException) {
                log.error("Falha ao executar rollback na tabela '{}': {}", tableName, rollbackException.getMessage());
            }
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

            StringBuilder colListBuilder = new StringBuilder();
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                if (columnIndex > 0) colListBuilder.append(", ");
                colListBuilder.append(helper.enquoteIdentifier(columns.get(columnIndex), true));
            }
            String colList = colListBuilder.toString();

            String placeholders = ",?".repeat(columns.size()).substring(1);
            String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(quotedTable, colList, placeholders);

            try (PreparedStatement insertStmt = conn.prepareStatement(sql)) {
                int batchCount = 0;
                for (Map<String, Object> row : rows) {
                    for (int columnPosition = 0; columnPosition < columns.size(); columnPosition++) {
                        String columnName = columns.get(columnPosition);
                        Object value = convertValue(row.get(columnName), columnTypes.get(columnName), columnName);
                        insertStmt.setObject(columnPosition + 1, value);
                    }

                    insertStmt.addBatch();
                    batchCount++;

                    if (batchCount % batchSize == 0) {
                        insertStmt.executeBatch();
                        insertStmt.clearBatch();
                        log.debug("Batch parcial executado: {} linhas", batchCount);
                    }
                }

                if (batchCount % batchSize != 0) {
                    insertStmt.executeBatch();
                }

            } catch (SQLIntegrityConstraintViolationException constraintViolation) {
                if (constraintViolation.getErrorCode() == MYSQL_ERR_NULL_VIOLATION) {
                    log.warn("Violação NOT NULL na tabela '{}': {}", tableName, constraintViolation.getMessage());
                    throw new ConflictException(
                            ErrorCode.MIGRATION_NULL_VIOLATION,
                            "Campo obrigatório recebeu valor nulo do SharePoint na tabela '%s': %s"
                                    .formatted(tableName, constraintViolation.getMessage()));
                }
                log.warn("Conflito de integridade na tabela '{}': {}", tableName, constraintViolation.getMessage());
                throw new ConflictException(
                        ErrorCode.MIGRATION_CONFLICT,
                        "Conflito de integridade ao inserir dados na tabela '%s': %s"
                                .formatted(tableName, constraintViolation.getMessage()));
            }
        }
    }

    private Object convertValue(Object value, FieldMapping mapping, String column) {
        if (value == null || mapping == null) return value;

        String raw = value instanceof String stringValue ? stringValue : null;
        if (raw == null) return value;

        ColumnType type = mapping.type();
        String nativeType = mapping.nativeType() != null ? mapping.nativeType().toUpperCase() : null;

        boolean isDatetime = type == ColumnType.DATETIME
                || (nativeType != null && (nativeType.startsWith("DATETIME") || nativeType.startsWith("TIMESTAMP")));
        boolean isDate =
                !isDatetime && (type == ColumnType.DATE || (nativeType != null && nativeType.startsWith("DATE")));

        if (isDatetime) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(raw), ZoneOffset.UTC);
            } catch (Exception parseException) {
                log.warn(
                        "Falha ao converter coluna '{}' para DATETIME — valor='{}' não é ISO 8601 válido, passando raw",
                        column,
                        raw);
                return value;
            }
        }
        if (isDate) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(raw), ZoneOffset.UTC)
                        .toLocalDate();
            } catch (Exception parseException) {
                log.warn(
                        "Falha ao converter coluna '{}' para DATE — valor='{}' não é ISO 8601 válido, passando raw",
                        column,
                        raw);
                return value;
            }
        }
        return value;
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
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Nome de tabela inválido: %s".formatted(tableName));
        }
    }

    private void validateNoNestedPaths(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return;
        rows.getFirst().keySet().stream()
                .filter(key -> key.contains("."))
                .findFirst()
                .ifPresent(key -> {
                    throw new BadRequestException(
                            ErrorCode.BAD_REQUEST,
                            "Nome de coluna inválido para SQL: '%s' — dot-notation é exclusivo do adapter MongoDB"
                                    .formatted(key));
                });
    }
}
