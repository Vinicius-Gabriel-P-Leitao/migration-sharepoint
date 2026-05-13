package org.migration.sharepoint.infra.writer.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.model.FieldMapping;

class MySqlMigrationWriterTest {

    private final MySqlMigrationWriter writer = new MySqlMigrationWriter(null);

    @Test
    void shouldConvertEmptyStringToNullForNumericAndDateTypes() {
        FieldMapping numMapping = FieldMapping.builder().type(ColumnType.NUMBER).build();
        FieldMapping dateMapping = FieldMapping.builder().type(ColumnType.DATE).build();
        FieldMapping textMapping = FieldMapping.builder().type(ColumnType.TEXT).build();

        assertThat(writer.convert("", numMapping)).isNull();
        assertThat(writer.convert("  ", numMapping)).isNull();
        assertThat(writer.convert("", dateMapping)).isNull();
        assertThat(writer.convert("", textMapping)).isEqualTo("");
    }

    @Test
    void shouldResolveTypeByStrippingRedundantConstraints() {
        FieldMapping mapping = FieldMapping.builder()
                .type(ColumnType.NUMBER)
                .nativeType("BIGINT NOT NULL AUTO_INCREMENT")
                .build();

        assertThat(writer.resolveType(mapping)).isEqualTo("BIGINT");
    }

    @Test
    void shouldResolveCanonicalTypeWhenNativeTypeIsMissing() {
        FieldMapping mapping = FieldMapping.builder().type(ColumnType.NUMBER).build();

        assertThat(writer.resolveType(mapping)).isEqualTo("BIGINT");
    }

    @Test
    void shouldResolveVarcharForTextPrimaryKey() {
        FieldMapping mapping =
                FieldMapping.builder().type(ColumnType.TEXT).primaryKey(true).build();

        assertThat(writer.resolveType(mapping)).isEqualTo("VARCHAR(255)");
    }
}
