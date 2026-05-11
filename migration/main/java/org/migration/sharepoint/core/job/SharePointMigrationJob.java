/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.job;

import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.data.enums.JobStatus;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.model.MigrationJob;
import org.migration.sharepoint.data.model.MigrationLog;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.exception.base.AppException;
import org.migration.sharepoint.infra.graph.GraphClient;
import org.migration.sharepoint.infra.writer.MySqlMigrationWriter;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private MySqlMigrationWriter writer;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long jobId = context.getJobDetail().getJobDataMap().getLong("jobId");
        log.info("Iniciando execução do job id={}", jobId);

        MigrationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobExecutionException("Job " + jobId + " não encontrado"));

        MigrationLog migrationLog = logRepository.save(MigrationLog.builder()
                .job(job)
                .status(JobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build());

        try {
            List<Map<String, Object>> raw = graphClient.fetchListItems(
                    job.getSiteId(),
                    job.getListId(),
                    job.getFieldMappings().keySet());

            List<Map<String, Object>> mapped = applyFieldMapping(raw, job.getFieldMappings());

            writer.write(job.getConnectionString(), job.getTableName(), mapped);

            migrationLog.setStatus(JobStatus.SUCCESS);
            migrationLog.setFinishedAt(LocalDateTime.now());
            logRepository.save(migrationLog);

            log.info("Job id={} concluído. {} registros migrados", jobId, mapped.size());

            if (job.getScheduleType() == ScheduleType.CONTINUOUS) {
                context.getScheduler().triggerJob(context.getJobDetail().getKey());
            }

        } catch (AppException e) {
            log.warn("Job id={} falhou: {}", jobId, e.getMessage());
            migrationLog.setStatus(JobStatus.FAILED);
            migrationLog.setFinishedAt(LocalDateTime.now());
            migrationLog.setErrorMessage(e.getMessage());
            logRepository.save(migrationLog);

        } catch (Exception e) {
            log.error("Job id={} falhou com erro inesperado", jobId, e);
            migrationLog.setStatus(JobStatus.FAILED);
            migrationLog.setFinishedAt(LocalDateTime.now());
            migrationLog.setErrorMessage(e.getMessage());
            logRepository.save(migrationLog);
            throw new JobExecutionException(e);
        }
    }

    private List<Map<String, Object>> applyFieldMapping(List<Map<String, Object>> rows,
            Map<String, String> fieldMappings) {
        return rows.stream().map(row -> {
            Map<String, Object> out = new LinkedHashMap<>();
            fieldMappings.forEach((spField, dbColumn) -> {
                if (row.containsKey(spField)) {
                    out.put(dbColumn, row.get(spField));
                }
            });
            return out;
        }).toList();
    }
}
