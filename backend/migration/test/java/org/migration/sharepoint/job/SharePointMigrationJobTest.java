package org.migration.sharepoint.job;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.migration.sharepoint.core.job.SharePointMigrationJob;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.JobStatus;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;
import org.migration.sharepoint.data.model.JobNode;
import org.migration.sharepoint.data.model.MigrationJob;
import org.migration.sharepoint.data.model.MigrationLog;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.migration.sharepoint.infra.graph.GraphClient;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.MigrationWriterRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

@ExtendWith(MockitoExtension.class)
class SharePointMigrationJobTest {

    @Mock
    private MigrationJobRepository jobRepository;

    @Mock
    private MigrationLogRepository logRepository;

    @Mock
    private GraphClient graphClient;

    @Mock
    private MigrationWriterRegistry writerRegistry;

    @Mock
    private JobExecutionContext context;

    @Mock
    private JobDetail jobDetail;

    @Mock
    private Scheduler scheduler;

    @InjectMocks
    private SharePointMigrationJob migrationJob;

    private static final Long JOB_ID = 42L;
    private static final Map<String, FieldMapping> FIELD_MAPPINGS = Map.of(
            "Title",
            new FieldMapping("title", ColumnType.TEXT, null, false, false),
            "Amount",
            new FieldMapping("amount", ColumnType.NUMBER, null, false, false));

    @BeforeEach
    void setUpContext() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("jobId", JOB_ID);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getJobDataMap()).thenReturn(dataMap);
    }

    private MigrationJob buildJob(ScheduleType scheduleType) {
        return MigrationJob.builder()
                .id(JOB_ID)
                .name("Test Job")
                .pageSize(100)
                .targetDb(TargetDb.MYSQL)
                .connectionKey("MYSQL_PROD")
                .scheduleType(scheduleType)
                .migration(new JobNode(
                        null, "site-123", "list-456", "test_table", FIELD_MAPPINGS, Map.of(), List.of(), List.of()))
                .build();
    }

    private MigrationLog captureLog() {
        ArgumentCaptor<MigrationLog> captor = ArgumentCaptor.forClass(MigrationLog.class);
        verify(logRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    // -------------------------------------------------------------------------
    // execute — job not found
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowJobExecutionExceptionWhenJobNotFound() {
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> migrationJob.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining(String.valueOf(JOB_ID));
    }

    // -------------------------------------------------------------------------
    // execute — success path
    // -------------------------------------------------------------------------

    @Test
    void shouldSaveSuccessLogAfterSuccessfulMigration() throws Exception {
        MigrationJob job = buildJob(ScheduleType.MANUAL);
        List<Map<String, Object>> spData =
                List.of(Map.of("Title", "Row 1", "Amount", 10), Map.of("Title", "Row 2", "Amount", 20));

        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(eq("site-123"), eq("list-456"), anySet(), eq(100)))
                .thenReturn(spData);
        when(writerRegistry.get(TargetDb.MYSQL)).thenReturn(writer);

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.SUCCESS);
        assertThat(finalLog.getFinishedAt()).isNotNull();
        assertThat(finalLog.getErrorMessage()).isNull();
    }

    @Test
    void shouldPassMappedRowsToWriter() throws Exception {
        MigrationJob job = buildJob(ScheduleType.MANUAL);
        List<Map<String, Object>> spData = List.of(Map.of("Title", "Hello", "Amount", 42));

        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt())).thenReturn(spData);
        when(writerRegistry.get(TargetDb.MYSQL)).thenReturn(writer);

        migrationJob.execute(context);

        ArgumentCaptor<List<Map<String, Object>>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(writer).write(eq("MYSQL_PROD"), eq("test_table"), rowsCaptor.capture(), any(), anyList(), any());

        List<Map<String, Object>> rows = rowsCaptor.getValue();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).containsEntry("title", "Hello").containsEntry("amount", 42);
    }

    @Test
    void shouldRequestOnlyFieldsDefinedInMapping() throws Exception {
        MigrationJob job = buildJob(ScheduleType.MANUAL);
        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("Title", "X", "Amount", 1)));
        when(writerRegistry.get(any())).thenReturn(writer);

        migrationJob.execute(context);

        ArgumentCaptor<Set<String>> fieldsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(graphClient).fetchListItems(any(), any(), fieldsCaptor.capture(), anyInt());
        assertThat(fieldsCaptor.getValue()).containsExactlyInAnyOrder("Title", "Amount");
    }

    // -------------------------------------------------------------------------
    // execute — empty row data (no SharePoint rows returned)
    // -------------------------------------------------------------------------

    @Test
    void shouldSaveSuccessLogWhenSharePointReturnsNoRows() throws Exception {
        MigrationJob job = buildJob(ScheduleType.MANUAL);
        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(writerRegistry.get(any())).thenReturn(writer);

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.SUCCESS);
    }

    // -------------------------------------------------------------------------
    // execute — field mapping produces all-empty rows
    // -------------------------------------------------------------------------

    @Test
    void shouldSaveFailedLogWhenNoFieldsMappedFromSharePointData() throws Exception {
        MigrationJob job = buildJob(ScheduleType.MANUAL);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("UnknownField1", "value1"), Map.of("UnknownField2", "value2")));

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).isNotBlank();
        verify(writerRegistry, never()).get(any());
    }

    // -------------------------------------------------------------------------
    // execute — AppException from Graph API
    // -------------------------------------------------------------------------

    @Test
    void shouldSaveFailedLogWhenGraphApiReturns401() throws Exception {
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(buildJob(ScheduleType.MANUAL)));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenThrow(new InfrastructureException(ErrorCode.GRAPH_UNAUTHORIZED, "Token rejeitado (HTTP 401)"));

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).contains("Token rejeitado");
    }

    @Test
    void shouldSaveFailedLogWhenGraphApiReturns429() throws Exception {
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(buildJob(ScheduleType.MANUAL)));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenThrow(new InfrastructureException(ErrorCode.GRAPH_RATE_LIMITED, "Rate limit atingido (HTTP 429)"));

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).contains("Rate limit");
    }

    // -------------------------------------------------------------------------
    // execute — unexpected exception
    // -------------------------------------------------------------------------

    @Test
    void shouldSaveFailedLogAndRethrowOnUnexpectedException() throws Exception {
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(buildJob(ScheduleType.MANUAL)));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("Unexpected failure"));

        assertThatThrownBy(() -> migrationJob.execute(context)).isInstanceOf(JobExecutionException.class);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).contains("Unexpected failure");
    }

    @Test
    void shouldUseClassNameAsErrorMessageWhenExceptionMessageIsNull() throws Exception {
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(buildJob(ScheduleType.MANUAL)));
        when(logRepository.save(any())).thenReturn(runningLog);
        RuntimeException exceptionWithNoMessage = new NullPointerException();
        when(graphClient.fetchListItems(any(), any(), any(), anyInt())).thenThrow(exceptionWithNoMessage);

        assertThatThrownBy(() -> migrationJob.execute(context)).isInstanceOf(JobExecutionException.class);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(finalLog.getErrorMessage()).isEqualTo("NullPointerException");
    }

    // -------------------------------------------------------------------------
    // execute — CONTINUOUS mode
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerNextRunAfterSuccessWhenContinuousMode() throws Exception {
        MigrationJob job = buildJob(ScheduleType.CONTINUOUS);
        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();
        JobKey jobKey = JobKey.jobKey("job-" + JOB_ID, "migration");

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("Title", "X", "Amount", 1)));
        when(writerRegistry.get(any())).thenReturn(writer);
        when(context.getScheduler()).thenReturn(scheduler);
        when(jobDetail.getKey()).thenReturn(jobKey);

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.SUCCESS);
        verify(scheduler).triggerJob(jobKey);
    }

    @Test
    void shouldKeepSuccessStatusWhenContinuousModeRescheduleFails() throws Exception {
        MigrationJob job = buildJob(ScheduleType.CONTINUOUS);
        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();
        JobKey jobKey = JobKey.jobKey("job-" + JOB_ID, "migration");

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("Title", "X", "Amount", 1)));
        when(writerRegistry.get(any())).thenReturn(writer);
        when(context.getScheduler()).thenReturn(scheduler);
        when(jobDetail.getKey()).thenReturn(jobKey);
        doThrow(new SchedulerException("Scheduler down")).when(scheduler).triggerJob(jobKey);

        migrationJob.execute(context);

        MigrationLog finalLog = captureLog();
        assertThat(finalLog.getStatus()).isEqualTo(JobStatus.SUCCESS);
    }

    @Test
    void shouldNotTriggerNextRunForManualScheduleType() throws Exception {
        MigrationJob job = buildJob(ScheduleType.MANUAL);
        MigrationWriter writer = mock(MigrationWriter.class);
        MigrationLog runningLog = MigrationLog.builder().id(1L).build();

        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(runningLog);
        when(graphClient.fetchListItems(any(), any(), any(), anyInt()))
                .thenReturn(List.of(Map.of("Title", "X", "Amount", 1)));
        when(writerRegistry.get(any())).thenReturn(writer);

        migrationJob.execute(context);

        verify(context, never()).getScheduler();
    }
}
