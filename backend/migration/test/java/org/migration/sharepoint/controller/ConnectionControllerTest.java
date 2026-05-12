package org.migration.sharepoint.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.migration.sharepoint.controller.connection.ConnectionController;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.migration.sharepoint.infra.connection.ConnectionRegistry.ConnectionSummary;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.ConflictException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConnectionController.class)
@TestPropertySource(properties = "security.rate-limit.enabled=false")
class ConnectionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ConnectionRegistry registry;

  // -------------------------------------------------------------------------
  // GET /v1/connections
  // -------------------------------------------------------------------------

  @Test
  void shouldReturnEmptyListWhenNoConnectionsRegistered() throws Exception {
    when(registry.list()).thenReturn(List.of());

    mockMvc
        .perform(get("/v1/connections"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void shouldReturnAllRegisteredConnections() throws Exception {
    when(registry.list())
        .thenReturn(
            List.of(
                new ConnectionSummary("MYSQL_PROD", "MySQL Produção"),
                new ConnectionSummary("PG_DEV", "PostgreSQL Dev")));

    mockMvc
        .perform(get("/v1/connections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("MYSQL_PROD"))
        .andExpect(jsonPath("$[0].name").value("MySQL Produção"))
        .andExpect(jsonPath("$[1].key").value("PG_DEV"));
  }

  @Test
  void shouldNeverExposeUrlInListResponse() throws Exception {
    when(registry.list()).thenReturn(List.of(new ConnectionSummary("MYSQL_PROD", "MySQL")));

    mockMvc
        .perform(get("/v1/connections"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].url").doesNotExist());
  }

  // -------------------------------------------------------------------------
  // POST /v1/connections
  // -------------------------------------------------------------------------

  @Test
  void shouldRegisterConnectionAndReturn201() throws Exception {
    doNothing().when(registry).register(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"MYSQL_PROD","name":"MySQL Produção","url":"jdbc:mysql://localhost:3306/db"}
                    """))
        .andExpect(status().isCreated());

    verify(registry).register("MYSQL_PROD", "MySQL Produção", "jdbc:mysql://localhost:3306/db");
  }

  @Test
  void shouldReturn409WhenKeyAlreadyRegistered() throws Exception {
    doThrow(
            new ConflictException(
                ErrorCode.CONNECTION_KEY_CONFLICT, "Chave 'MYSQL_PROD' já registrada"))
        .when(registry)
        .register(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"MYSQL_PROD","name":"MySQL","url":"jdbc:mysql://localhost:3306/db"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldReturn400WhenKeyIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"","name":"MySQL","url":"jdbc:mysql://localhost:3306/db"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenKeyContainsLowercaseLetters() throws Exception {
    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"mysql_prod","name":"MySQL","url":"jdbc:mysql://localhost:3306/db"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenNameIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"MYSQL_PROD","name":"","url":"jdbc:mysql://localhost:3306/db"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenUrlIsBlank() throws Exception {
    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"MYSQL_PROD","name":"MySQL","url":""}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldReturn400WhenUrlIsInvalidScheme() throws Exception {
    doThrow(
            new org.migration.sharepoint.infra.exception.custom.BadRequestException(
                ErrorCode.BAD_REQUEST, "URL inválida"))
        .when(registry)
        .register(anyString(), anyString(), anyString());

    mockMvc
        .perform(
            post("/v1/connections")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"key":"BAD","name":"Bad","url":"http://not-a-db.com"}
                    """))
        .andExpect(status().isBadRequest());
  }

  // -------------------------------------------------------------------------
  // DELETE /v1/connections/{key}
  // -------------------------------------------------------------------------

  @Test
  void shouldDeleteConnectionAndReturn204() throws Exception {
    doNothing().when(registry).remove("MYSQL_PROD");

    mockMvc.perform(delete("/v1/connections/MYSQL_PROD")).andExpect(status().isNoContent());

    verify(registry).remove("MYSQL_PROD");
  }

  @Test
  void shouldReturn404WhenDeletingUnknownKey() throws Exception {
    doThrow(
            new NotFoundException(
                ErrorCode.CONNECTION_NOT_FOUND, "Conexão 'NONEXISTENT' não encontrada"))
        .when(registry)
        .remove("NONEXISTENT");

    mockMvc.perform(delete("/v1/connections/NONEXISTENT")).andExpect(status().isNotFound());
  }
}
