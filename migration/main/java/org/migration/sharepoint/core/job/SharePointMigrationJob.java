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

      List<Map<String, Object>> mapped = applyFieldMapping(raw, job.getFieldMappings(), jobId);

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
      log.warn(
          "Job id={} falhou: [{}] {}",
          jobId,
          appException.getErrorCode(),
          appException.getMessage());
      fail(migrationLog, appException.getMessage());

    } catch (Exception unexpectedException) {
      String errorMessage =
          unexpectedException.getMessage() != null
              ? unexpectedException.getMessage()
              : unexpectedException.getClass().getSimpleName();
      log.error("Job id={} falhou com erro inesperado", jobId, unexpectedException);
      fail(migrationLog, errorMessage);
      throw new JobExecutionException(unexpectedException);
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
    List<Map<String, Object>> mapped =
        rows.stream()
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

    if (!mapped.isEmpty() && mapped.stream().allMatch(Map::isEmpty)) {
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

    return mapped;
  }
}
