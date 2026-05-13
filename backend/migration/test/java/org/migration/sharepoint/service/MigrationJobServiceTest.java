package org.migration.sharepoint.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.migration.sharepoint.controller.job.dto.JobRequest;
import org.migration.sharepoint.controller.job.dto.JobResponse;
import org.migration.sharepoint.controller.job.dto.LogResponse;
import org.migration.sharepoint.core.service.MigrationJobService;
import org.migration.sharepoint.core.service.QuartzSchedulerService;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.JobStatus;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;
import org.migration.sharepoint.data.model.JobNode;
import org.migration.sharepoint.data.model.MigrationJob;
import org.migration.sharepoint.data.model.MigrationLog;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigrationJobServiceTest {

    @Mock
    private MigrationJobRepository jobRepository;

    @Mock
    private MigrationLogRepository logRepository;

    @Mock
    private QuartzSchedulerService quartzSchedulerService;

    @Mock
    private ConnectionRegistry connectionRegistry;

    @InjectMocks
    private MigrationJobService service;

    private static final Map<String, FieldMapping> FIELD_MAPPINGS =
            Map.of("Title", new FieldMapping("title", ColumnType.TEXT, null));

    private static final JobNode ROOT_NODE = new JobNode("site-123", "list-456", "test_table", FIELD_MAPPINGS, null);

    private MigrationJob buildJob(Long id, ScheduleType scheduleType) {
        return MigrationJob.builder()
                .id(id)
                .name("Test Job")
                .pageSize(100)
                .targetDb(TargetDb.MYSQL)
                .connectionKey("MYSQL_PROD")
                .scheduleType(scheduleType)
                .migration(ROOT_NODE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private JobRequest buildRequest(ScheduleType scheduleType) {
        return new JobRequest("Test Job", 100, TargetDb.MYSQL, "MYSQL_PROD", scheduleType, null, null, null, ROOT_NODE);
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenNoJobs() {
        when(jobRepository.findAll()).thenReturn(List.of());
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void shouldReturnAllJobs() {
        MigrationJob job1 = buildJob(1L, ScheduleType.MANUAL);
        MigrationJob job2 = buildJob(2L, ScheduleType.CRON);
        job2.setCronExpression("0 0 * * *");
        when(jobRepository.findAll()).thenReturn(List.of(job1, job2));
        assertThat(service.findAll()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnJobWhenFound() {
        MigrationJob job = buildJob(1L, ScheduleType.MANUAL);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        JobResponse response = service.findById(1L);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Test Job");
        assertThat(response.migration().siteId()).isEqualTo("site-123");
    }

    @Test
    void shouldThrowNotFoundWhenJobDoesNotExist() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    // -------------------------------------------------------------------------
    // create — schedule validation
    // -------------------------------------------------------------------------

    @Test
    void shouldCreateManualJobSuccessfully() {
        MigrationJob saved = buildJob(1L, ScheduleType.MANUAL);
        when(connectionRegistry.resolveUrl("MYSQL_PROD")).thenReturn("jdbc:mysql://localhost/db");
        when(jobRepository.save(any())).thenReturn(saved);

        JobResponse response = service.create(buildRequest(ScheduleType.MANUAL));

        assertThat(response.name()).isEqualTo("Test Job");
        verify(quartzSchedulerService).schedule(any());
    }

    @Test
    void shouldCreateIntervalJobWithAllRequiredFields() {
        MigrationJob saved = buildJob(1L, ScheduleType.INTERVAL);
        saved.setIntervalValue(30L);
        saved.setIntervalUnit(IntervalUnit.MINUTES);
        when(connectionRegistry.resolveUrl("MYSQL_PROD")).thenReturn("jdbc:mysql://localhost/db");
        when(jobRepository.save(any())).thenReturn(saved);

        JobRequest request = new JobRequest(
                "Test Job",
                100,
                TargetDb.MYSQL,
                "MYSQL_PROD",
                ScheduleType.INTERVAL,
                30L,
                IntervalUnit.MINUTES,
                null,
                ROOT_NODE);

        JobResponse response = service.create(request);

        assertThat(response.scheduleType()).isEqualTo(ScheduleType.INTERVAL);
        verify(quartzSchedulerService).schedule(any());
    }

    @Test
    void shouldRejectIntervalJobMissingIntervalValue() {
        JobRequest request = new JobRequest(
                "Test Job",
                100,
                TargetDb.MYSQL,
                "MYSQL_PROD",
                ScheduleType.INTERVAL,
                null,
                IntervalUnit.MINUTES,
                null,
                ROOT_NODE);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("intervalValue");
    }

    @Test
    void shouldRejectIntervalJobMissingIntervalUnit() {
        JobRequest request = new JobRequest(
                "Test Job", 100, TargetDb.MYSQL, "MYSQL_PROD", ScheduleType.INTERVAL, 30L, null, null, ROOT_NODE);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("intervalUnit");
    }

    @Test
    void shouldCreateCronJobWithValidExpression() {
        MigrationJob saved = buildJob(1L, ScheduleType.CRON);
        saved.setCronExpression("0 0 * * *");
        when(connectionRegistry.resolveUrl("MYSQL_PROD")).thenReturn("jdbc:mysql://localhost/db");
        when(jobRepository.save(any())).thenReturn(saved);

        JobRequest request = new JobRequest(
                "Test Job", 100, TargetDb.MYSQL, "MYSQL_PROD", ScheduleType.CRON, null, null, "0 0 * * *", ROOT_NODE);

        JobResponse response = service.create(request);

        assertThat(response.scheduleType()).isEqualTo(ScheduleType.CRON);
    }

    @Test
    void shouldRejectCronJobWithNullExpression() {
        JobRequest request = new JobRequest(
                "Test Job", 100, TargetDb.MYSQL, "MYSQL_PROD", ScheduleType.CRON, null, null, null, ROOT_NODE);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cronExpression");
    }

    @Test
    void shouldRejectCronJobWithBlankExpression() {
        JobRequest request = new JobRequest(
                "Test Job", 100, TargetDb.MYSQL, "MYSQL_PROD", ScheduleType.CRON, null, null, "   ", ROOT_NODE);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cronExpression");
    }

    // -------------------------------------------------------------------------
    // create — connectionKey validation
    // -------------------------------------------------------------------------

    @Test
    void shouldRejectCreateWithUnregisteredConnectionKey() {
        doThrow(new NotFoundException(null, "Conexão 'MISSING_KEY' não encontrada"))
                .when(connectionRegistry)
                .resolveUrl("MYSQL_PROD");

        assertThatThrownBy(() -> service.create(buildRequest(ScheduleType.MANUAL)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("MISSING_KEY");

        verify(jobRepository, never()).save(any());
        verify(quartzSchedulerService, never()).schedule(any());
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    void shouldUpdateJobSuccessfully() {
        MigrationJob existing = buildJob(1L, ScheduleType.MANUAL);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(connectionRegistry.resolveUrl("MYSQL_PROD")).thenReturn("jdbc:mysql://localhost/db");
        when(jobRepository.save(any())).thenReturn(existing);

        JobResponse response = service.update(1L, buildRequest(ScheduleType.MANUAL));

        assertThat(response.id()).isEqualTo(1L);
        verify(quartzSchedulerService).reschedule(any());
    }

    @Test
    void shouldThrowNotFoundOnUpdateWhenJobMissing() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, buildRequest(ScheduleType.MANUAL)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void shouldRejectUpdateWithInvalidConnectionKey() {
        doThrow(new NotFoundException(null, "Conexão 'MYSQL_PROD' não encontrada"))
                .when(connectionRegistry)
                .resolveUrl("MYSQL_PROD");

        assertThatThrownBy(() -> service.update(1L, buildRequest(ScheduleType.MANUAL)))
                .isInstanceOf(NotFoundException.class);

        verify(jobRepository, never()).findById(any());
        verify(quartzSchedulerService, never()).reschedule(any());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void shouldDeleteJobSuccessfully() {
        MigrationJob job = buildJob(1L, ScheduleType.MANUAL);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        service.delete(1L);

        verify(quartzSchedulerService).unschedule(1L);
        verify(jobRepository).delete(job);
    }

    @Test
    void shouldThrowNotFoundOnDeleteWhenJobMissing() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");

        verify(quartzSchedulerService, never()).unschedule(any());
        verify(jobRepository, never()).delete(any());
    }

    // -------------------------------------------------------------------------
    // runNow
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerJobImmediately() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(buildJob(1L, ScheduleType.MANUAL)));

        service.runNow(1L);

        verify(quartzSchedulerService).triggerNow(1L);
    }

    @Test
    void shouldThrowNotFoundOnRunNowWhenJobMissing() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runNow(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");

        verify(quartzSchedulerService, never()).triggerNow(any());
    }

    // -------------------------------------------------------------------------
    // findLogs
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnLogsForExistingJob() {
        MigrationJob job = buildJob(1L, ScheduleType.MANUAL);
        MigrationLog log = MigrationLog.builder()
                .id(10L)
                .job(job)
                .status(JobStatus.SUCCESS)
                .startedAt(LocalDateTime.now().minusMinutes(2))
                .finishedAt(LocalDateTime.now())
                .build();

        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(logRepository.findByJobIdOrderByStartedAtDesc(1L)).thenReturn(List.of(log));

        List<LogResponse> logs = service.findLogs(1L);

        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().status()).isEqualTo(JobStatus.SUCCESS);
    }

    @Test
    void shouldReturnEmptyLogsWhenNoneExist() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(buildJob(1L, ScheduleType.MANUAL)));
        when(logRepository.findByJobIdOrderByStartedAtDesc(1L)).thenReturn(List.of());

        assertThat(service.findLogs(1L)).isEmpty();
    }

    @Test
    void shouldThrowNotFoundOnLogsWhenJobMissing() {
        when(jobRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLogs(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
    }
}
