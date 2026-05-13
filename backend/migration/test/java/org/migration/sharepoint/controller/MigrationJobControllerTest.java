package org.migration.sharepoint.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.migration.sharepoint.controller.job.MigrationJobController;
import org.migration.sharepoint.controller.job.dto.JobResponse;
import org.migration.sharepoint.controller.job.dto.LogResponse;
import org.migration.sharepoint.core.service.MigrationJobService;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.JobStatus;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;
import org.migration.sharepoint.data.model.JobNode;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MigrationJobController.class)
@TestPropertySource(properties = "security.rate-limit.enabled=false")
class MigrationJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MigrationJobService service;

    private static final Map<String, FieldMapping> FIELD_MAPPINGS =
            Map.of("Title", new FieldMapping("title", ColumnType.TEXT, null, false, false));

    private static final JobNode ROOT_NODE =
            new JobNode(null, "site-123", "list-456", "test_table", FIELD_MAPPINGS, Map.of(), List.of(), List.of());

    private JobResponse buildJobResponse(Long id, ScheduleType scheduleType) {
        return new JobResponse(
                id,
                "Test Job",
                100,
                TargetDb.MYSQL,
                "MYSQL_PROD",
                scheduleType,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                ROOT_NODE);
    }

    private static final String VALID_JOB_JSON = """
            {
              "name": "Test Job",
              "pageSize": 100,
              "targetDb": "MYSQL",
              "connectionKey": "MYSQL_PROD",
              "scheduleType": "MANUAL",
              "migration": {
                "siteId": "site-123",
                "listId": "list-456",
                "tableName": "test_table",
                "fieldMappings": {"Title": {"column": "title", "type": "TEXT", "primaryKey": false}},
                "customFields": {},
                "foreignKeys": [],
                "children": []
              }
            }
            """;

    // -------------------------------------------------------------------------
    // GET /v1/jobs
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnAllJobs() throws Exception {
        when(service.findAll())
                .thenReturn(
                        List.of(buildJobResponse(1L, ScheduleType.MANUAL), buildJobResponse(2L, ScheduleType.CRON)));

        mockMvc.perform(get("/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldReturnEmptyArrayWhenNoJobsExist() throws Exception {
        when(service.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // -------------------------------------------------------------------------
    // GET /v1/jobs/{id}
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnJobById() throws Exception {
        when(service.findById(1L)).thenReturn(buildJobResponse(1L, ScheduleType.MANUAL));

        mockMvc.perform(get("/v1/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Job"))
                .andExpect(jsonPath("$.connectionKey").value("MYSQL_PROD"))
                .andExpect(jsonPath("$.migration.siteId").value("site-123"))
                .andExpect(jsonPath("$.migration.tableName").value("test_table"));
    }

    @Test
    void shouldReturn404WhenJobNotFound() throws Exception {
        when(service.findById(999L))
                .thenThrow(new NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job id=999 não encontrado"));

        mockMvc.perform(get("/v1/jobs/999")).andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/v1/jobs/not-a-number")).andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // POST /v1/jobs
    // -------------------------------------------------------------------------

    @Test
    void shouldCreateJobAndReturn201() throws Exception {
        when(service.create(any())).thenReturn(buildJobResponse(1L, ScheduleType.MANUAL));

        mockMvc.perform(post("/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_JOB_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturn400WhenJobNameIsBlank() throws Exception {
        String json = """
                {
                  "name": "",
                  "pageSize": 100,
                  "targetDb": "MYSQL",
                  "connectionKey": "MYSQL_PROD",
                  "scheduleType": "MANUAL",
                  "migration": {
                    "siteId": "site-123",
                    "listId": "list-456",
                    "tableName": "test_table",
                    "fieldMappings": {"Title": {"column": "title", "type": "TEXT", "primaryKey": false}},
                    "customFields": {},
                    "foreignKeys": [],
                    "children": []
                  }
                }
                """;

        mockMvc.perform(post("/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenPageSizeExceedsMax() throws Exception {
        String json = """
                {
                  "name": "Job",
                  "pageSize": 9999,
                  "targetDb": "MYSQL",
                  "connectionKey": "MYSQL_PROD",
                  "scheduleType": "MANUAL",
                  "migration": {
                    "siteId": "site-123",
                    "listId": "list-456",
                    "tableName": "test_table",
                    "fieldMappings": {"Title": {"column": "title", "type": "TEXT", "primaryKey": false}},
                    "customFields": {},
                    "foreignKeys": [],
                    "children": []
                  }
                }
                """;

        mockMvc.perform(post("/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenPageSizeIsZero() throws Exception {
        String json = """
                {
                  "name": "Job",
                  "pageSize": 0,
                  "targetDb": "MYSQL",
                  "connectionKey": "MYSQL_PROD",
                  "scheduleType": "MANUAL",
                  "migration": {
                    "siteId": "site-123",
                    "listId": "list-456",
                    "tableName": "test_table",
                    "fieldMappings": {"Title": {"column": "title", "type": "TEXT", "primaryKey": false}},
                    "customFields": {},
                    "foreignKeys": [],
                    "children": []
                  }
                }
                """;

        mockMvc.perform(post("/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenConnectionKeyNotRegistered() throws Exception {
        when(service.create(any()))
                .thenThrow(
                        new NotFoundException(ErrorCode.CONNECTION_NOT_FOUND, "Conexão 'MYSQL_PROD' não encontrada"));

        mockMvc.perform(post("/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(VALID_JOB_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400WhenIntervalJobMissingRequiredFields() throws Exception {
        when(service.create(any()))
                .thenThrow(
                        new BadRequestException(ErrorCode.BAD_REQUEST, "scheduleType INTERVAL requer intervalValue"));

        String json = """
                {
                  "name": "Job",
                  "pageSize": 100,
                  "targetDb": "MYSQL",
                  "connectionKey": "MYSQL_PROD",
                  "scheduleType": "INTERVAL",
                  "migration": {
                    "siteId": "site-123",
                    "listId": "list-456",
                    "tableName": "test_table",
                    "fieldMappings": {"Title": {"column": "title", "type": "TEXT", "primaryKey": false}},
                    "customFields": {},
                    "foreignKeys": [],
                    "children": []
                  }
                }
                """;

        mockMvc.perform(post("/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // PUT /v1/jobs/{id}
    // -------------------------------------------------------------------------

    @Test
    void shouldUpdateJobAndReturn200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(buildJobResponse(1L, ScheduleType.MANUAL));

        mockMvc.perform(put("/v1/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JOB_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentJob() throws Exception {
        when(service.update(eq(999L), any()))
                .thenThrow(new NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job id=999 não encontrado"));

        mockMvc.perform(put("/v1/jobs/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JOB_JSON))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /v1/jobs/{id}
    // -------------------------------------------------------------------------

    @Test
    void shouldDeleteJobAndReturn204() throws Exception {
        doNothing().when(service).delete(1L);

        mockMvc.perform(delete("/v1/jobs/1")).andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentJob() throws Exception {
        doThrow(new NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job id=999 não encontrado"))
                .when(service)
                .delete(999L);

        mockMvc.perform(delete("/v1/jobs/999")).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // POST /v1/jobs/{id}/run
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerJobAndReturn202() throws Exception {
        doNothing().when(service).runNow(1L);

        mockMvc.perform(post("/v1/jobs/1/run")).andExpect(status().isAccepted());

        verify(service).runNow(1L);
    }

    @Test
    void shouldReturn404WhenRunningNonExistentJob() throws Exception {
        doThrow(new NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job id=999 não encontrado"))
                .when(service)
                .runNow(999L);

        mockMvc.perform(post("/v1/jobs/999/run")).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /v1/jobs/{id}/logs
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnLogsForJob() throws Exception {
        LogResponse log = new LogResponse(
                10L, 1L, JobStatus.SUCCESS, LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), null);
        when(service.findLogs(1L)).thenReturn(List.of(log));

        mockMvc.perform(get("/v1/jobs/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    void shouldReturnEmptyLogsArray() throws Exception {
        when(service.findLogs(1L)).thenReturn(List.of());

        mockMvc.perform(get("/v1/jobs/1/logs"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldReturn404WhenGettingLogsForMissingJob() throws Exception {
        when(service.findLogs(999L))
                .thenThrow(new NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job id=999 não encontrado"));

        mockMvc.perform(get("/v1/jobs/999/logs")).andExpect(status().isNotFound());
    }
}
