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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;
import org.migration.sharepoint.data.model.ForeignKeyDefinition;
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
    public List<Long> write(
            String connectionKey,
            String targetName,
            List<Map<String, Object>> rows,
            Map<String, FieldMapping> columnTypes,
            List<ForeignKeyDefinition> foreignKeys,
            String parentTableName) {
        validateTableName(targetName);
        validateNoNestedPaths(rows);

        try (Connection connection = connectionPool.getConnection(connectionKey)) {
            String catalog = connection.getCatalog();

            // Sincroniza o schema: CRIA ou ALTERA a tabela
            syncSchema(connection, catalog, targetName, columnTypes, foreignKeys, parentTableName);

            if (rows.isEmpty()) {
                log.info(
                        "Job id={}: Nenhum dado para migrar para a tabela '{}'. Estrutura verificada/sincronizada.",
                        targetName);
                return List.of();
            }

            List<String> tableColumns = fetchTableColumns(connection, catalog, targetName);

            // Filtra as linhas para conterem apenas o que existe no Banco físico
            List<Map<String, Object>> validRows = filterValidRows(rows, tableColumns);

            if (validRows.isEmpty()) {
                log.warn("Nenhuma linha possui campos que correspondam às colunas da tabela '{}'", targetName);
                return List.of();
            }

            // Identifica as colunas que realmente serão inseridas (interseção dados x Banco)
            List<String> insertColumns = tableColumns.stream()
                    .filter(columnName -> validRows.getFirst().containsKey(columnName))
                    .collect(Collectors.toList());

            return replaceAll(connection, targetName, insertColumns, validRows, columnTypes);

        } catch (SQLException sqlException) {
            throw new InfrastructureException(
                    ErrorCode.DB_CONNECTION_ERROR,
                    "Erro de conexão com o banco de dados: %s".formatted(sqlException.getMessage()));
        }
    }

    private void syncSchema(
            Connection connection,
            String catalog,
            String tableName,
            Map<String, FieldMapping> columnTypes,
            List<ForeignKeyDefinition> foreignKeys,
            String parentTableName)
            throws SQLException {

        if (!tableExists(connection, catalog, tableName)) {
            createTable(connection, tableName, columnTypes, foreignKeys, parentTableName);
            return;
        }

        // Se a tabela já existe, verificamos se precisamos adicionar colunas ou FKs
        updateTableSchema(connection, catalog, tableName, columnTypes, foreignKeys, parentTableName);
    }

    private boolean tableExists(Connection connection, String catalog, String tableName) throws SQLException {
        try (PreparedStatement checkStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            checkStatement.setString(1, catalog);
            checkStatement.setString(2, tableName);

            try (ResultSet resultSet = checkStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    private void createTable(
            Connection connection,
            String tableName,
            Map<String, FieldMapping> columnTypes,
            List<ForeignKeyDefinition> foreignKeys,
            String parentTableName)
            throws SQLException {

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

            // Adiciona Foreign Keys se houver contexto de pai
            if (parentTableName != null && foreignKeys != null && !foreignKeys.isEmpty()) {
                String quotedParent = statement.enquoteIdentifier(parentTableName, true);
                for (ForeignKeyDefinition fk : foreignKeys) {
                    columnDefs
                            .append(", CONSTRAINT ")
                            .append(statement.enquoteIdentifier(
                                    "fk_%s_%s".formatted(tableName, fk.localColumn()), true))
                            .append(" FOREIGN KEY (")
                            .append(statement.enquoteIdentifier(fk.localColumn(), true))
                            .append(") REFERENCES ")
                            .append(quotedParent)
                            .append(" (")
                            .append(statement.enquoteIdentifier(fk.parentColumn(), true))
                            .append(")");
                }
            }

            statement.execute("CREATE TABLE %s (%s)".formatted(quotedTable, columnDefs));
            log.info("Tabela '{}' criada com {} colunas baseada na configuração", tableName, columnTypes.size());
        }
    }

    private void updateTableSchema(
            Connection connection,
            String catalog,
            String tableName,
            Map<String, FieldMapping> columnTypes,
            List<ForeignKeyDefinition> foreignKeys,
            String parentTableName)
            throws SQLException {

        List<String> existingColumns = fetchTableColumns(connection, catalog, tableName);

        try (Statement statement = connection.createStatement()) {
            // 1. Adiciona colunas faltantes
            for (Map.Entry<String, FieldMapping> entry : columnTypes.entrySet()) {
                if (!existingColumns.contains(entry.getKey())) {
                    String sqlType = resolveType(entry.getKey(), entry.getValue());
                    statement.execute("ALTER TABLE %s ADD COLUMN %s %s"
                            .formatted(
                                    statement.enquoteIdentifier(tableName, true),
                                    statement.enquoteIdentifier(entry.getKey(), true),
                                    sqlType));
                    log.info("Coluna '{}' adicionada à tabela '{}'", entry.getKey(), tableName);
                }
            }

            // 2. Adiciona FKs faltantes
            if (parentTableName != null && foreignKeys != null) {
                List<String> existingConstraints = fetchTableConstraints(connection, catalog, tableName);
                String quotedTable = statement.enquoteIdentifier(tableName, true);
                String quotedParent = statement.enquoteIdentifier(parentTableName, true);

                for (ForeignKeyDefinition fk : foreignKeys) {
                    String constraintName = "fk_%s_%s".formatted(tableName, fk.localColumn());
                    if (!existingConstraints.contains(constraintName)) {
                        statement.execute("ALTER TABLE %s ADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s (%s)"
                                .formatted(
                                        quotedTable,
                                        statement.enquoteIdentifier(constraintName, true),
                                        statement.enquoteIdentifier(fk.localColumn(), true),
                                        quotedParent,
                                        statement.enquoteIdentifier(fk.parentColumn(), true)));
                        log.info("FK '{}' adicionada à tabela '{}'", constraintName, tableName);
                    }
                }
            }
        }
    }

    private List<String> fetchTableConstraints(Connection connection, String catalog, String tableName)
            throws SQLException {
        List<String> constraints = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
            preparedStatement.setString(1, catalog);
            preparedStatement.setString(2, tableName);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                constraints.add(resultSet.getString("CONSTRAINT_NAME"));
            }
        }
        return constraints;
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

    private List<Long> replaceAll(
            Connection connection,
            String tableName,
            List<String> columns,
            List<Map<String, Object>> rows,
            Map<String, FieldMapping> columnTypes)
            throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            // Desabilita checagem de FK para permitir o DELETE/REPLACE de uma árvore complexa
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");

            try {
                // TRUNCATE reseta o AUTO_INCREMENT e é mais performático para tabelas grandes
                statement.execute("TRUNCATE TABLE %s".formatted(statement.enquoteIdentifier(tableName, true)));

                // Deduplicação em memória para evitar gaps no AUTO_INCREMENT causados pelo INSERT IGNORE
                List<Map<String, Object>> deduplicatedRows = deduplicateRows(rows, columnTypes);

                List<Long> keys = batchInsert(connection, tableName, columns, deduplicatedRows, columnTypes);
                connection.commit();
                return keys;
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (SQLException sqlException) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                log.error("Falha ao executar rollback na tabela '{}': {}", tableName, rollbackException.getMessage());
            }
            throw sqlException;
        }
    }

    private List<Map<String, Object>> deduplicateRows(
            List<Map<String, Object>> rows, Map<String, FieldMapping> columnTypes) {
        List<String> uniqueColumns = columnTypes.values().stream()
                .filter(mapping -> mapping.primaryKey() || mapping.uniqueKey())
                .map(FieldMapping::column)
                .toList();

        if (uniqueColumns.isEmpty()) return rows;

        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> deduplicated = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            // Gera uma chave baseada em todos os campos únicos combinados
            String key = uniqueColumns.stream()
                    .map(columnName -> String.valueOf(row.get(columnName)))
                    .collect(Collectors.joining("|"));

            if (seen.add(key)) {
                deduplicated.add(row);
            }
        }

        if (rows.size() != deduplicated.size()) {
            log.info(
                    "Deduplicação em memória: {} duplicados removidos para manter sequência de IDs.",
                    rows.size() - deduplicated.size());
        }

        return deduplicated;
    }

    private List<Long> batchInsert(
            Connection connection,
            String tableName,
            List<String> columns,
            List<Map<String, Object>> rows,
            Map<String, FieldMapping> columnTypes)
            throws SQLException {

        String sql = buildInsertSql(connection, tableName, columns);
        List<Long> generatedKeys = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int currentBatchSize = 0;

            for (Map<String, Object> row : rows) {
                setStatementValues(preparedStatement, columns, row, columnTypes);
                preparedStatement.addBatch();
                currentBatchSize++;

                if (currentBatchSize >= batchSize) {
                    executeAndCollectKeys(preparedStatement, generatedKeys);
                    currentBatchSize = 0;
                }
            }

            if (currentBatchSize > 0) {
                executeAndCollectKeys(preparedStatement, generatedKeys);
            }
        } catch (SQLIntegrityConstraintViolationException exception) {
            handleConstraintViolation(tableName, exception);
        }

        return generatedKeys;
    }

    private String buildInsertSql(Connection connection, String tableName, List<String> columns) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            String quotedTable = statement.enquoteIdentifier(tableName, true);
            String columnList = columns.stream()
                    .map(columnName -> {
                        try {
                            return statement.enquoteIdentifier(columnName, true);
                        } catch (SQLException exception) {
                            return columnName;
                        }
                    })
                    .collect(Collectors.joining(", "));

            String placeholders = ",?".repeat(columns.size()).substring(1);
            return "INSERT IGNORE INTO %s (%s) VALUES (%s)".formatted(quotedTable, columnList, placeholders);
        }
    }

    private void setStatementValues(
            PreparedStatement preparedStatement,
            List<String> columns,
            Map<String, Object> row,
            Map<String, FieldMapping> columnTypes)
            throws SQLException {
        for (int index = 0; index < columns.size(); index++) {
            String columnName = columns.get(index);
            Object value = convertValue(row.get(columnName), columnTypes.get(columnName), columnName);
            preparedStatement.setObject(index + 1, value);
        }
    }

    private void executeAndCollectKeys(PreparedStatement preparedStatement, List<Long> keys) throws SQLException {
        preparedStatement.executeBatch();
        try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
            while (resultSet.next()) {
                keys.add(resultSet.getLong(1));
            }
        }
        preparedStatement.clearBatch();
    }

    private void handleConstraintViolation(String tableName, SQLIntegrityConstraintViolationException exception) {
        if (exception.getErrorCode() == MYSQL_ERR_NULL_VIOLATION) {
            throw new ConflictException(
                    ErrorCode.MIGRATION_NULL_VIOLATION, "Campo obrigatório nulo na tabela '%s'".formatted(tableName));
        }
        throw new ConflictException(
                ErrorCode.MIGRATION_CONFLICT,
                "Conflito de integridade na tabela '%s': %s".formatted(tableName, exception.getMessage()));
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

    private List<String> fetchTableColumns(Connection connection, String catalog, String tableName)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION")) {
            preparedStatement.setString(1, catalog);
            preparedStatement.setString(2, tableName);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME"));
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
