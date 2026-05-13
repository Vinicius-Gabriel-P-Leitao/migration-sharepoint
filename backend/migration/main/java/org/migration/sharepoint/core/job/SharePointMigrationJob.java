/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.job;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.data.enums.JobStatus;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.*;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.base.AppException;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.graph.GraphClient;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.MigrationWriterRegistry;
import org.quartz.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DisallowConcurrentExecution
public class SharePointMigrationJob implements Job {

    private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MigrationJobRepository jobRepository;

    @Autowired
    private MigrationLogRepository logRepository;

    @Autowired
    private GraphClient graphClient;

    @Autowired
    private MigrationWriterRegistry writerRegistry;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long jobId = context.getJobDetail().getJobDataMap().getLong("jobId");
        MDC.put("jobId", String.valueOf(jobId));

        try {
            executeInternal(jobId, context);
        } finally {
            MDC.remove("jobId");
        }
    }

    private void executeInternal(Long jobId, JobExecutionContext context) throws JobExecutionException {
        log.info("Iniciando execução do job id={}", jobId);

        MigrationJob job = jobRepository
                .findById(jobId)
                .orElseThrow(() -> new JobExecutionException("Job %d não encontrado".formatted(jobId)));

        MigrationLog migrationLog = logRepository.save(MigrationLog.builder()
                .job(job)
                .status(JobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build());

        try {
            JobNode rootNode = job.getMigration();
            int total = syncNode(rootNode, job.getConnectionKey(), job.getTargetDb(), job.getPageSize(), jobId, null);

            if (rootNode.children() != null && !rootNode.children().isEmpty()) {
                total += syncTree(
                        rootNode.children(),
                        job.getConnectionKey(),
                        job.getTargetDb(),
                        job.getPageSize(),
                        jobId,
                        rootNode.tableName());
            }

            migrationLog.setStatus(JobStatus.SUCCESS);
            migrationLog.setFinishedAt(LocalDateTime.now());
            logRepository.save(migrationLog);

            log.info("Job id={} concluído — {} registros migrados (total, todos os nós)", jobId, total);

            if (job.getScheduleType() == ScheduleType.CONTINUOUS) {
                try {
                    context.getScheduler().triggerJob(context.getJobDetail().getKey());
                } catch (SchedulerException schedulerException) {
                    log.error("Job id={} CONTINUOUS: falha ao reagendar próxima execução", jobId, schedulerException);
                }
            }

        } catch (AppException appException) {
            log.warn("Job id={} falhou: [{}] {}", jobId, appException.getErrorCode(), appException.getMessage());
            fail(migrationLog, appException.getMessage());

        } catch (Exception unexpectedException) {
            String errorMessage = unexpectedException.getMessage() != null
                    ? unexpectedException.getMessage()
                    : unexpectedException.getClass().getSimpleName();
            log.error("Job id={} falhou com erro inesperado", jobId, unexpectedException);
            fail(migrationLog, errorMessage);
            throw new JobExecutionException(unexpectedException);
        }
    }

    private Object generateCustomValue(CustomFieldDefinition definition) {
        return switch (definition.function()) {
            case CURRENT_TIMESTAMP_UTC_3 -> OffsetDateTime.now(ZONE_BR).toLocalDateTime();
            case CURRENT_DATE_BR -> OffsetDateTime.now(ZONE_BR).toLocalDate();
            case UUID_GEN -> UUID.randomUUID().toString();
            case STATIC_VALUE -> definition.staticValue();
        };
    }

    private int syncNode(JobNode node, String connectionKey, TargetDb targetDb, int pageSize, Long jobId, String parentTableName) {
        List<Map<String, Object>> rawData = graphClient.fetchListItems(
                node.siteId(), node.listId(), node.fieldMappings().keySet(), pageSize);

        List<Map<String, Object>> mappedData = applyFieldMapping(rawData, node.fieldMappings(), jobId);

        // Injeta campos virtuais (Custom Fields)
        if (node.customFields() != null && !node.customFields().isEmpty()) {
            injectCustomFields(mappedData, node.customFields());
        }

        Map<String, FieldMapping> allMappings = new HashMap<>();
        node.fieldMappings().values().forEach(fm -> allMappings.put(fm.column(), fm));

        // Os campos personalizados também precisam ser conhecidos pelo autor da DDL
        Map<String, FieldMapping> combinedTypes = new HashMap<>(allMappings);
        if (node.customFields() != null) {
            node.customFields().values().forEach(customField -> {
                combinedTypes.put(
                        customField.column(),
                        new FieldMapping(
                                customField.column(), customField.type(), customField.nativeType(), false, false));
            });
        }

        MigrationWriter writer = writerRegistry.get(targetDb);
        writer.write(connectionKey, node.tableName(), mappedData, combinedTypes, node.foreignKeys(), parentTableName);

        log.info("Job id={} Nodo={}: {} registros migrados", jobId, node.tableName(), mappedData.size());
        return mappedData.size();
    }

    private void injectCustomFields(List<Map<String, Object>> rows, Map<String, CustomFieldDefinition> customFields) {
        rows.forEach(row -> {
            customFields.forEach((key, definition) -> {
                row.put(definition.column(), generateCustomValue(definition));
            });
        });
    }

    private int syncTree(List<JobNode> nodes, String connectionKey, TargetDb targetDb, int pageSize, Long jobId, String parentTableName) {
        if (nodes == null || nodes.isEmpty()) return 0;

        // INFO: Usamos virtual thread para api mais atual do java e trabalhamos com futures
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>(nodes.size());

            for (JobNode node : nodes) {
                futures.add(executor.submit(() -> {
                    int count = syncNode(node, connectionKey, targetDb, pageSize, jobId, parentTableName);
                    count += syncTree(node.children(), connectionKey, targetDb, pageSize, jobId, node.tableName());

                    return count;
                }));
            }

            int total = 0;
            for (Future<Integer> future : futures) {
                try {
                    total += future.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }

            return total;
        }
    }

    private void fail(MigrationLog migrationLog, String errorMessage) {
        migrationLog.setStatus(JobStatus.FAILED);
        migrationLog.setFinishedAt(LocalDateTime.now());
        migrationLog.setErrorMessage(errorMessage);
        logRepository.save(migrationLog);
    }

    private List<Map<String, Object>> applyFieldMapping(
            List<Map<String, Object>> rows, Map<String, FieldMapping> fieldMappings, Long jobId) {

        Map<String, FieldMapping> normalizedMappings = new HashMap<>();
        fieldMappings.forEach((fieldName, mapping) -> normalizedMappings.put(fieldName.toLowerCase(), mapping));

        List<Map<String, Object>> mappedRows = rows.stream()
                .map(row -> {
                    Map<String, Object> outputRow = new LinkedHashMap<>();

                    row.forEach((actualKey, value) -> {
                        FieldMapping mapping = normalizedMappings.get(actualKey.toLowerCase());
                        if (mapping != null) {
                            outputRow.put(mapping.column(), value);
                        }
                    });
                    return outputRow;
                })
                .collect(Collectors.toList());

        if (!mappedRows.isEmpty() && mappedRows.stream().allMatch(Map::isEmpty)) {
            throw new BadRequestException(
                    ErrorCode.MIGRATION_EMPTY_MAPPING,
                    "Nenhum campo do fieldMappings encontrado nos dados retornados pelo SharePoint.");
        }

        return mappedRows;
    }
}
