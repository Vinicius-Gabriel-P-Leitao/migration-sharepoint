package org.migration.sharepoint.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.migration.sharepoint.controller.adapter.AdapterController;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.MigrationWriterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdapterController.class)
@TestPropertySource(properties = "security.rate-limit.enabled=false")
class AdapterControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MigrationWriterRegistry writerRegistry;

  // -------------------------------------------------------------------------
  // GET /v1/adapters/{targetDb}/types
  // -------------------------------------------------------------------------

  @Test
  void shouldReturnMysqlTypes() throws Exception {
    MigrationWriter writer = mock(MigrationWriter.class);
    when(writer.canonicalMapping())
        .thenReturn(
            Map.of(
                ColumnType.TEXT, "TEXT",
                ColumnType.NUMBER, "BIGINT",
                ColumnType.DECIMAL, "DECIMAL(15,2)",
                ColumnType.BOOLEAN, "TINYINT(1)",
                ColumnType.DATE, "DATE",
                ColumnType.DATETIME, "DATETIME(6)"));
    when(writer.nativeTypes())
        .thenReturn(
            List.of(
                "TINYINT",
                "SMALLINT",
                "INT",
                "BIGINT",
                "DECIMAL",
                "FLOAT",
                "DOUBLE",
                "CHAR",
                "VARCHAR",
                "TEXT",
                "DATE",
                "DATETIME",
                "TIMESTAMP",
                "BOOLEAN",
                "JSON"));
    when(writerRegistry.get(TargetDb.MYSQL)).thenReturn(writer);

    mockMvc
        .perform(get("/v1/adapters/MYSQL/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canonical").isMap())
        .andExpect(jsonPath("$.canonical.TEXT").value("TEXT"))
        .andExpect(jsonPath("$.canonical.NUMBER").value("BIGINT"))
        .andExpect(jsonPath("$.nativeTypes").isArray());
  }

  @Test
  void shouldReturnPostgresqlTypes() throws Exception {
    MigrationWriter writer = mock(MigrationWriter.class);
    when(writer.canonicalMapping())
        .thenReturn(
            Map.of(
                ColumnType.TEXT, "TEXT",
                ColumnType.NUMBER, "BIGINT",
                ColumnType.DECIMAL, "NUMERIC(15,2)",
                ColumnType.BOOLEAN, "BOOLEAN",
                ColumnType.DATE, "DATE",
                ColumnType.DATETIME, "TIMESTAMP"));
    when(writer.nativeTypes()).thenReturn(List.of("TEXT", "BIGINT", "NUMERIC", "BOOLEAN", "DATE", "TIMESTAMP"));
    when(writerRegistry.get(TargetDb.POSTGRESQL)).thenReturn(writer);

    mockMvc
        .perform(get("/v1/adapters/POSTGRESQL/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canonical.BOOLEAN").value("BOOLEAN"));
  }

  @Test
  void shouldReturnMongodbTypes() throws Exception {
    MigrationWriter writer = mock(MigrationWriter.class);
    when(writer.canonicalMapping()).thenReturn(Map.of(ColumnType.TEXT, "String"));
    when(writer.nativeTypes()).thenReturn(List.of("String", "Int32", "Int64", "Double", "Boolean", "Date", "ObjectId"));
    when(writerRegistry.get(TargetDb.MONGODB)).thenReturn(writer);

    mockMvc
        .perform(get("/v1/adapters/MONGODB/types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nativeTypes[0]").value("String"));
  }

  @Test
  void shouldReturn400WhenTargetDbIsInvalid() throws Exception {
    mockMvc
        .perform(get("/v1/adapters/ORACLE/types"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn502WhenNoWriterFoundForTargetDb() throws Exception {
    when(writerRegistry.get(TargetDb.MYSQL))
        .thenThrow(
            new InfrastructureException(
                ErrorCode.TARGET_DB_NOT_SUPPORTED,
                "Nenhum writer disponível para o banco: MYSQL"));

    mockMvc
        .perform(get("/v1/adapters/MYSQL/types"))
        .andExpect(status().isBadRequest());
  }
}
