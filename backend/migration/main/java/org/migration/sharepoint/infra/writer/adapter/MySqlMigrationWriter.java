/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

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

        try (Connection conn = connectionPool.getConnection(connectionKey)) {
            String catalog = conn.getCatalog();

            // Synchronize o schema: CRIA a tabela se não existir usando a CONFIGURATION do Job
            createTableIfAbsent(conn, catalog, targetName, columnTypes);

            if (rows.isEmpty()) {
                log.info(
                        "Job id={}: Nenhum dado para migrar para a tabela '{}'. Estrutura verificada/criada.",
                        targetName);
                return;
            }

            List<String> tableColumns = fetchTableColumns(conn, catalog, targetName);

            // Filtrate as linhas para conterem arenas o que existe no Banco físico
            List<Map<String, Object>> validRows = filterValidRows(rows, tableColumns);

            if (validRows.isEmpty()) {
                log.warn("Nenhuma linha possui campos que correspondam às colunas da tabela '{}'", targetName);
                return;
            }

            // Identifica as columns que realignment serão inseridas (interseção dados x Banco)
            List<String> insertColumns = tableColumns.stream()
                    .filter(col -> validRows.getFirst().containsKey(col))
                    .collect(Collectors.toList());

            replaceAll(conn, targetName, insertColumns, validRows, columnTypes);
            log.info("{} linhas migradas para a tabela '{}' (full replace)", validRows.size(), targetName);

        } catch (SQLException sqlException) {
            throw new InfrastructureException(
                    ErrorCode.DB_CONNECTION_ERROR,
                    "Erro de conexão com o banco de dados: %s".formatted(sqlException.getMessage()));
        }
    }

    private void createTableIfAbsent(
            Connection connection, String catalog, String tableName, Map<String, FieldMapping> columnTypes)
            throws SQLException {

        try (PreparedStatement checkStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            checkStatement.setString(1, catalog);
            checkStatement.setString(2, tableName);

            try (ResultSet resultSet = checkStatement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) > 0) return;
            }
        }

        try (Statement statement = connection.createStatement()) {
            String quotedTable = statement.enquoteIdentifier(tableName, true);

            StringBuilder columnDefs = new StringBuilder();
            List<String> columns = new ArrayList<>(columnTypes.keySet());
            String primaryKeyColumn = null;
            List<String> uniqueColumns = new ArrayList<>();

            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                if (columnIndex > 0) columnDefs.append(", ");

                String columnName = columns.get(columnIndex);
                FieldMapping mapping = columnTypes.get(columnName);
                String sqlType = resolveType(columnName, mapping);

                columnDefs
                        .append(statement.enquoteIdentifier(columnName, true))
                        .append(" ")
                        .append(sqlType);

                if (mapping == null) continue;

                if (mapping.primaryKey()) {
                    primaryKeyColumn = columnName;
                }

                if (mapping.uniqueKey() && !mapping.primaryKey()) {
                    uniqueColumns.add(columnName);
                }
            }

            if (primaryKeyColumn != null) {
                columnDefs
                        .append(", PRIMARY KEY (")
                        .append(statement.enquoteIdentifier(primaryKeyColumn, true))
                        .append(")");
            }

            for (String uniqueColumn : uniqueColumns) {
                columnDefs
                        .append(", UNIQUE (")
                        .append(statement.enquoteIdentifier(uniqueColumn, true))
                        .append(")");
            }

            statement.execute("CREATE TABLE %s (%s)".formatted(quotedTable, columnDefs));
            log.info("Tabela '{}' criada com {} colunas baseada na configuração", tableName, columnTypes.size());
        }
    }

    private String resolveType(String column, FieldMapping mapping) {
        if (mapping == null) {
            return "TEXT";
        }

        if (mapping.nativeType() != null && !mapping.nativeType().isBlank()) {
            return validateAndReturnNative(column, mapping.nativeType());
        }

        if (mapping.type() != null) {
            String resolved = MYSQL_CANONICAL_MAP.get(mapping.type());
            if (resolved != null) {
                // Prevenção do erro: "BLOB/TEXT column used in key specification without a key length"
                // Se for PK e o tipo resolvido for TEXT, forçamos um VARCHAR estável.
                if (mapping.primaryKey() && "TEXT".equalsIgnoreCase(resolved)) {
                    return "VARCHAR(255)";
                }

                return resolved;
            }
        }

        return "TEXT";
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
                .orElse(null);

        if (matchedDef == null) return nativeType;

        validateParams(column, upper, matchedDef);
        return nativeType;
    }

    private void validateParams(String column, String upperType, NativeTypeDefinition matchedDef) {
        String innerParams = upperType.contains("(") ? upperType.replaceAll("^[A-Z]+\\((.+)\\)$", "$1") : "";

        if (innerParams.isBlank()) return;

        String[] rawParams = innerParams.split(",");
        if (rawParams.length != matchedDef.params().size()) return;

        for (int paramIndex = 0; paramIndex < matchedDef.params().size(); paramIndex++) {
            NativeTypeDefinition.ParamSpec spec = matchedDef.params().get(paramIndex);
            try {
                int val = Integer.parseInt(rawParams[paramIndex].trim());
                if (val < spec.min() || val > spec.max()) {
                    log.warn(
                            "Coluna '{}': Parâmetro {}={} fora do range [{}, {}]. Prosseguindo sob risco do banco.",
                            column,
                            spec.label(),
                            val,
                            spec.min(),
                            spec.max());
                }
            } catch (Exception ignored) {
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
            Connection connection,
            String tableName,
            List<String> columns,
            List<Map<String, Object>> rows,
            Map<String, FieldMapping> columnTypes)
            throws SQLException {
        try (Statement helperStatement = connection.createStatement()) {
            String quotedTable = helperStatement.enquoteIdentifier(tableName, true);

            StringBuilder columnListBuilder = new StringBuilder();
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                if (columnIndex > 0) columnListBuilder.append(", ");
                columnListBuilder.append(helperStatement.enquoteIdentifier(columns.get(columnIndex), true));
            }
            String columnList = columnListBuilder.toString();

            String placeholders = ",?".repeat(columns.size()).substring(1);
            String sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(quotedTable, columnList, placeholders);

            try (PreparedStatement insertStatement = connection.prepareStatement(sql)) {
                int batchCount = 0;
                for (Map<String, Object> row : rows) {
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        String columnName = columns.get(columnIndex);
                        Object value = convertValue(row.get(columnName), columnTypes.get(columnName), columnName);
                        insertStatement.setObject(columnIndex + 1, value);
                    }

                    insertStatement.addBatch();
                    batchCount++;

                    if (batchCount % batchSize == 0) {
                        insertStatement.executeBatch();
                        insertStatement.clearBatch();
                    }
                }

                if (batchCount % batchSize != 0) {
                    insertStatement.executeBatch();
                }

            } catch (SQLIntegrityConstraintViolationException constraintViolation) {
                if (constraintViolation.getErrorCode() == MYSQL_ERR_NULL_VIOLATION) {
                    throw new ConflictException(
                            ErrorCode.MIGRATION_NULL_VIOLATION,
                            "Campo obrigatório recebeu valor nulo na tabela '%s'".formatted(tableName));
                }
                throw new ConflictException(
                        ErrorCode.MIGRATION_CONFLICT, "Conflito de integridade na tabela '%s'".formatted(tableName));
            }
        }
    }

    private Object convertValue(Object value, FieldMapping mapping, String column) {
        if (value == null || mapping == null) return null;

        ColumnType columnType = mapping.type();
        String nativeType = mapping.nativeType() != null ? mapping.nativeType().toUpperCase() : "";

        if (isBooleanType(columnType, nativeType)) {
            return handleBooleanConversion(value);
        }

        if (isNumericType(columnType)) {
            return handleNumericConversion(value, columnType);
        }

        if (isTemporalType(columnType, nativeType)) {
            return handleTemporalConversion(value, columnType, nativeType);
        }

        return value;
    }

    private boolean isBooleanType(ColumnType columnType, String nativeType) {
        return columnType == ColumnType.BOOLEAN || "TINYINT(1)".equals(nativeType);
    }

    private boolean isNumericType(ColumnType columnType) {
        return columnType == ColumnType.NUMBER || columnType == ColumnType.DECIMAL;
    }

    private boolean isTemporalType(ColumnType columnType, String nativeType) {
        return columnType == ColumnType.DATETIME
                || columnType == ColumnType.DATE
                || nativeType.startsWith("DATETIME")
                || nativeType.startsWith("TIMESTAMP")
                || nativeType.startsWith("DATE");
    }

    private Object handleBooleanConversion(Object value) {
        if (value instanceof Boolean boolValue) return boolValue ? 1 : 0;
        if (value instanceof String stringValue) {
            String cleanValue = stringValue.trim().toLowerCase();
            if (List.of("true", "1", "yes").contains(cleanValue)) return 1;
            if (List.of("false", "0", "no").contains(cleanValue)) return 0;
        }
        return value;
    }

    private Object handleNumericConversion(Object value, ColumnType columnType) {
        if (!(value instanceof String stringValue)) return value;
        try {
            String cleanedValue = stringValue.trim();
            return columnType == ColumnType.NUMBER ? Long.parseLong(cleanedValue) : Double.parseDouble(cleanedValue);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private Object handleTemporalConversion(Object value, ColumnType columnType, String nativeType) {
        try {
            LocalDateTime localDateTime = resolveToLocalDateTime(value);
            if (localDateTime == null) return value;

            boolean isDatetime = columnType == ColumnType.DATETIME
                    || nativeType.startsWith("DATETIME")
                    || nativeType.startsWith("TIMESTAMP");

            if (isDatetime) {
                return localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return localDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return value;
        }
    }

    private LocalDateTime resolveToLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof LocalDate localDate) return localDate.atStartOfDay();
        if (value instanceof String stringValue) {
            // Converte UTC (SharePoint) para Horário de Brasília
            return LocalDateTime.ofInstant(Instant.parse(stringValue), ZONE_BR);
        }
        return null;
    }

    private List<Map<String, Object>> filterValidRows(List<Map<String, Object>> rows, List<String> tableColumns) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> filtered = new LinkedHashMap<>();
                    row.forEach((key, value) -> {
                        if (tableColumns.contains(key)) {
                            filtered.put(key, value);
                        }
                    });
                    return filtered;
                })
                .filter(map -> !map.isEmpty())
                .collect(Collectors.toList());
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
                            ErrorCode.BAD_REQUEST, "Nome de coluna inválido para SQL: '%s'".formatted(key));
                });
    }
}
