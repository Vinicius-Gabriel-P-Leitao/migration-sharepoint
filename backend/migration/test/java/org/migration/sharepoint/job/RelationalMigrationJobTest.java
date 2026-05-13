package org.migration.sharepoint.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.migration.sharepoint.core.job.SharePointMigrationJob;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.*;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
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

@ExtendWith(MockitoExtension.class)
class RelationalMigrationJobTest {

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

    @InjectMocks
    private SharePointMigrationJob migrationJob;

    private static final Long JOB_ID = 1L;

    @BeforeEach
    void setUp() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("jobId", JOB_ID);
        lenient().when(context.getJobDetail()).thenReturn(jobDetail);
        lenient().when(jobDetail.getJobDataMap()).thenReturn(dataMap);
    }

    @Test
    void shouldHandleSelfNormalizationWithCleanSequencesAndNoNulls() throws Exception {
        // --- SCENARIO ---
        // A single SharePoint list with 4 items, but only 2 unique "Types".
        // Table 1 (Parent): tb_tipo_expediente (Unique by 'nome')
        // Table 2 (Child):  tb_expediente (FK to parent)
        
        List<Map<String, Object>> spData = List.of(
            Map.of("id", "SP-1", "TipoExpediente", "OFÍCIO",   "Numero", 100),
            Map.of("id", "SP-2", "TipoExpediente", "OFÍCIO",   "Numero", 101),
            Map.of("id", "SP-3", "TipoExpediente", "DESPACHO", "Numero", 200),
            Map.of("id", "SP-4", "TipoExpediente", "DESPACHO", "Numero", 201)
        );

        // Configure Parent Node (Normalization)
        JobNode parentNode = JobNode.builder()
            .tableName("tb_tipo_expediente")
            .fieldMappings(Map.of(
                "TipoExpediente", FieldMapping.builder().column("nome").type(ColumnType.TEXT).uniqueKey(true).build()
            ))
            .customFields(Map.of(
                "id", CustomFieldDefinition.builder().column("id").type(ColumnType.NUMBER).primaryKey(true).function(org.migration.sharepoint.data.enums.CustomFunction.AUTO_INCREMENT).build()
            ))
            .build();

        // Configure Child Node
        JobNode childNode = JobNode.builder()
            .tableName("tb_expediente")
            .fieldMappings(Map.of(
                "Numero", FieldMapping.builder().column("numero").type(ColumnType.NUMBER).build()
            ))
            .customFields(Map.of(
                "fk_tipo_expediente", CustomFieldDefinition.builder().column("fk_tipo_expediente").type(ColumnType.NUMBER).function(org.migration.sharepoint.data.enums.CustomFunction.STATIC_VALUE).staticValue("").build()
            ))
            .foreignKeys(List.of(new ForeignKeyDefinition("fk_tipo_expediente", "id")))
            .build();

        parentNode.setChildren(List.of(childNode));

        MigrationJob job = MigrationJob.builder()
            .id(JOB_ID)
            .targetDb(TargetDb.MYSQL)
            .connectionKey("CONN")
            .pageSize(500)
            .scheduleType(ScheduleType.MANUAL)
            .migration(parentNode)
            .build();

        // --- MOCKS ---
        MigrationWriter writer = mock(MigrationWriter.class);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(logRepository.save(any())).thenReturn(new MigrationLog());
        when(writerRegistry.get(TargetDb.MYSQL)).thenReturn(writer);
        
        // Return same data for both calls (since it's the same site/list in self-normalization)
        when(graphClient.fetchListItems(any(), any(), any(), anyInt())).thenReturn(spData);

        // Mock generated keys: Parent gets 1 and 2 (only 2 unique rows)
        when(writer.write(eq("CONN"), eq("tb_tipo_expediente"), anyList(), any(), any(), any()))
            .thenReturn(List.of(1L, 2L));
            
        // Mock generated keys for child
        when(writer.write(eq("CONN"), eq("tb_expediente"), anyList(), any(), any(), any()))
            .thenReturn(List.of(10L, 11L, 12L, 13L));

        // --- EXECUTION ---
        migrationJob.execute(context);

        // --- VERIFICATIONS ---

        // 1. Verify Parent Table (tb_tipo_expediente) only received UNIQUE rows
        ArgumentCaptor<List<Map<String, Object>>> parentRowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(writer).write(eq("CONN"), eq("tb_tipo_expediente"), parentRowsCaptor.capture(), any(), any(), isNull());
        
        List<Map<String, Object>> parentRows = parentRowsCaptor.getValue();
        assertThat(parentRows).hasSize(2);
        assertThat(parentRows.get(0)).containsEntry("nome", "OFÍCIO");
        assertThat(parentRows.get(1)).containsEntry("nome", "DESPACHO");

        // 2. Verify Child Table (tb_expediente) received ALL rows with CORRECT FKs (NO NULLS)
        ArgumentCaptor<List<Map<String, Object>>> childRowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(writer).write(eq("CONN"), eq("tb_expediente"), childRowsCaptor.capture(), any(), any(), eq("tb_tipo_expediente"));
        
        List<Map<String, Object>> childRows = childRowsCaptor.getValue();
        assertThat(childRows).hasSize(4);
        
        // Item 1 (OFÍCIO) -> FK should be 1
        assertThat(childRows.get(0)).containsEntry("numero", 100);
        assertThat(childRows.get(0)).containsEntry("fk_tipo_expediente", 1L);
        
        // Item 2 (OFÍCIO) -> FK should be 1 (even if filtered out of parent write)
        assertThat(childRows.get(1)).containsEntry("numero", 101);
        assertThat(childRows.get(1)).containsEntry("fk_tipo_expediente", 1L);
        
        // Item 3 (DESPACHO) -> FK should be 2
        assertThat(childRows.get(2)).containsEntry("numero", 200);
        assertThat(childRows.get(2)).containsEntry("fk_tipo_expediente", 2L);
        
        // Item 4 (DESPACHO) -> FK should be 2
        assertThat(childRows.get(3)).containsEntry("numero", 201);
        assertThat(childRows.get(3)).containsEntry("fk_tipo_expediente", 2L);

        // Ensure NO NULLS in FKs
        assertThat(childRows).extracting(row -> row.get("fk_tipo_expediente")).doesNotContainNull();
    }
}
