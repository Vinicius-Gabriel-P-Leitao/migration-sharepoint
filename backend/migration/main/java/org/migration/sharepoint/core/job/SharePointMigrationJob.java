/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.job;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
            JobNode migration = job.getMigration();
            int total = syncNode(
                    migration.siteId(),
                    migration.listId(),
                    migration.fieldMappings(),
                    migration.tableName(),
                    job.getConnectionKey(),
                    job.getTargetDb(),
                    job.getPageSize(),
                    jobId);

            if (migration.children() != null && !migration.children().isEmpty()) {
                total += syncTree(
                        migration.children(), job.getConnectionKey(), job.getTargetDb(), job.getPageSize(), jobId);
            }

            migrationLog.setStatus(JobStatus.SUCCESS);
            migrationLog.setFinishedAt(LocalDateTime.now());
            logRepository.save(migrationLog);

            log.info("Job id={} concluído — {} registros migrados (total, todos os nós)", jobId, total);

            if (job.getScheduleType() == ScheduleType.CONTINUOUS) {
                try {
                    context.getScheduler().triggerJob(context.getJobDetail().getKey());
                } catch (SchedulerException schedulerException) {
                    log.error(
                            "Job id={} CONTINUOUS: falha ao reagendar próxima execução — execução atual foi bem-sucedida",
                            jobId,
                            schedulerException);
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

    private int syncNode(
            String siteId,
            String listId,
            Map<String, FieldMapping> fieldMappings,
            String tableName,
            String connectionKey,
            TargetDb targetDb,
            int pageSize,
            Long jobId) {
        List<Map<String, Object>> raw = graphClient.fetchListItems(siteId, listId, fieldMappings.keySet(), pageSize);
        List<Map<String, Object>> mapped = applyFieldMapping(raw, fieldMappings, jobId);

        Map<String, FieldMapping> columnTypes =
                fieldMappings.values().stream().collect(Collectors.toMap(FieldMapping::column, fm -> fm));

        MigrationWriter writer = writerRegistry.get(targetDb);
        writer.write(connectionKey, tableName, mapped, columnTypes);

        log.info("Job id={}: {} registros migrados → {}.{}", jobId, mapped.size(), targetDb, tableName);
        return mapped.size();
    }

    private int syncTree(List<JobNode> nodes, String connectionKey, TargetDb targetDb, int pageSize, Long jobId) {
        if (nodes == null || nodes.isEmpty()) return 0;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>(nodes.size());
            for (JobNode node : nodes) {
                futures.add(executor.submit(() -> {
                    int count = syncNode(
                            node.siteId(),
                            node.listId(),
                            node.fieldMappings(),
                            node.tableName(),
                            connectionKey,
                            targetDb,
                            pageSize,
                            jobId);
                    count += syncTree(node.children(), connectionKey, targetDb, pageSize, jobId);
                    return count;
                }));
            }

            int total = 0;
            for (Future<Integer> future : futures) {
                try {
                    total += future.get();
                } catch (ExecutionException executionException) {
                    Throwable cause = executionException.getCause();
                    if (cause instanceof RuntimeException runtimeCause) throw runtimeCause;

                    throw new RuntimeException(cause);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Sync paralelo interrompido", interruptedException);
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

        // Normalizamos os campos configurados para lowercase para permitir busca O(1) insensível a caso.
        Map<String, FieldMapping> normalizedMappings = new HashMap<>();
        fieldMappings.forEach((fieldName, mapping) -> normalizedMappings.put(fieldName.toLowerCase(), mapping));

        List<Map<String, Object>> mappedRows = rows.stream()
                .map(row -> {
                    Map<String, Object> outputRow = new LinkedHashMap<>();
                    // Iteramos sobre os dados reais retornados e buscamos no nosso mapa de configuração normalizado
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
            List<String> expectedFields = List.copyOf(fieldMappings.keySet());
            log.warn(
                    "Job id={}: nenhum campo do fieldMappings encontrado nos dados do SharePoint. Campos esperados: {}",
                    jobId,
                    expectedFields);

            throw new BadRequestException(
                    ErrorCode.MIGRATION_EMPTY_MAPPING,
                    "Nenhum campo do fieldMappings encontrado nos dados retornados pelo SharePoint. "
                            + "Campos esperados: %s — verifique os nomes dos campos na configuração do job"
                                    .formatted(expectedFields));
        }

        return mappedRows;
    }
}
