/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.job;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.data.enums.JobStatus;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.model.FieldMapping;
import org.migration.sharepoint.data.model.MigrationJob;
import org.migration.sharepoint.data.model.MigrationLog;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.exception.base.AppException;
import org.migration.sharepoint.infra.graph.GraphClient;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.MigrationWriterRegistry;
import org.quartz.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@DisallowConcurrentExecution
public class SharePointMigrationJob implements Job {

  @Autowired private MigrationJobRepository jobRepository;

  @Autowired private MigrationLogRepository logRepository;

  @Autowired private GraphClient graphClient;

  @Autowired private MigrationWriterRegistry writerRegistry;

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

  private void executeInternal(Long jobId, JobExecutionContext context)
      throws JobExecutionException {
    log.info("Iniciando execução do job id={}", jobId);

    MigrationJob job =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new JobExecutionException("Job %d não encontrado".formatted(jobId)));

    MigrationLog migrationLog =
        logRepository.save(
            MigrationLog.builder()
                .job(job)
                .status(JobStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build());

    try {
      List<Map<String, Object>> raw =
          graphClient.fetchListItems(
              job.getSiteId(), job.getListId(), job.getFieldMappings().keySet(), job.getPageSize());

      List<Map<String, Object>> mapped = applyFieldMapping(raw, job.getFieldMappings());

      Map<String, FieldMapping> columnTypes =
          job.getFieldMappings().values().stream()
              .collect(Collectors.toMap(FieldMapping::column, fm -> fm));

      MigrationWriter writer = writerRegistry.get(job.getTargetDb());
      writer.write(job.getConnectionKey(), job.getTableName(), mapped, columnTypes);

      migrationLog.setStatus(JobStatus.SUCCESS);
      migrationLog.setFinishedAt(LocalDateTime.now());
      logRepository.save(migrationLog);

      log.info(
          "Job id={} concluído — {} registros migrados para {}/{}",
          jobId,
          mapped.size(),
          job.getTargetDb(),
          job.getTableName());

      if (job.getScheduleType() == ScheduleType.CONTINUOUS) {
        context.getScheduler().triggerJob(context.getJobDetail().getKey());
      }

    } catch (AppException appException) {
      log.warn(
          "Job id={} falhou: [{}] {}",
          jobId,
          appException.getErrorCode(),
          appException.getMessage());
      fail(migrationLog, appException.getMessage());

    } catch (Exception unexpectedException) {
      log.error("Job id={} falhou com erro inesperado", jobId, unexpectedException);
      fail(migrationLog, unexpectedException.getMessage());
      throw new JobExecutionException(unexpectedException);
    }
  }

  private void fail(MigrationLog log, String errorMessage) {
    log.setStatus(JobStatus.FAILED);
    log.setFinishedAt(LocalDateTime.now());
    log.setErrorMessage(errorMessage);
    logRepository.save(log);
  }

  private List<Map<String, Object>> applyFieldMapping(
      List<Map<String, Object>> rows, Map<String, FieldMapping> fieldMappings) {
    return rows.stream()
        .map(
            row -> {
              Map<String, Object> out = new LinkedHashMap<>();
              fieldMappings.forEach(
                  (spField, mapping) -> {
                    if (row.containsKey(spField)) {
                      out.put(mapping.column(), row.get(spField));
                    }
                  });
              return out;
            })
        .toList();
  }
}
