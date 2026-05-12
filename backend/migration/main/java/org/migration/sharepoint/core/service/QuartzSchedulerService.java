/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.core.job.SharePointMigrationJob;
import org.migration.sharepoint.data.model.MigrationJob;
import org.quartz.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuartzSchedulerService {

  private final Scheduler scheduler;

  public void schedule(MigrationJob job) {
    try {
      JobDetail detail = buildJobDetail(job);

      switch (job.getScheduleType()) {
        case MANUAL -> {
          scheduler.addJob(detail, true);
          log.info("Job id={} registrado sem agendamento (MANUAL)", job.getId());
        }
        case CONTINUOUS -> {
          scheduler.addJob(detail, true);
          scheduler.triggerJob(detail.getKey());
          log.info("Job id={} iniciado em modo CONTÍNUO", job.getId());
        }
        default -> {
          Trigger trigger = buildTrigger(job);
          scheduler.scheduleJob(detail, trigger);
          log.info("Job id={} agendado ({})", job.getId(), job.getScheduleType());
        }
      }
    } catch (SchedulerException schedulerException) {
      throw new RuntimeException(
          "Erro ao agendar job id=%d: %s".formatted(job.getId(), schedulerException.getMessage()),
          schedulerException);
    }
  }

  public void reschedule(MigrationJob job) {
    unschedule(job.getId());
    schedule(job);
  }

  public void unschedule(Long jobId) {
    try {
      scheduler.deleteJob(jobKey(jobId));
      log.info("Job id={} removido do Quartz", jobId);
    } catch (SchedulerException schedulerException) {
      throw new RuntimeException(
          "Erro ao remover job id=%d: %s".formatted(jobId, schedulerException.getMessage()),
          schedulerException);
    }
  }

  public void triggerNow(Long jobId) {
    try {
      scheduler.triggerJob(jobKey(jobId));
      log.info("Disparo manual do job id={}", jobId);
    } catch (SchedulerException schedulerException) {
      throw new RuntimeException(
          "Erro ao disparar job id=%d: %s".formatted(jobId, schedulerException.getMessage()),
          schedulerException);
    }
  }

  private JobDetail buildJobDetail(MigrationJob job) {
    return JobBuilder.newJob(SharePointMigrationJob.class)
        .withIdentity(jobKey(job.getId()))
        .withDescription(job.getName())
        .usingJobData("jobId", job.getId())
        .storeDurably()
        .build();
  }

  private Trigger buildTrigger(MigrationJob job) {
    TriggerBuilder<Trigger> builder =
        TriggerBuilder.newTrigger().withIdentity("trigger-" + job.getId(), "migration").startNow();

    return switch (job.getScheduleType()) {
      case INTERVAL -> buildIntervalTrigger(builder, job);
      case CRON ->
          builder.withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpression())).build();
      default -> builder.build();
    };
  }

  private Trigger buildIntervalTrigger(TriggerBuilder<Trigger> builder, MigrationJob job) {
    int value = job.getIntervalValue().intValue();
    SimpleScheduleBuilder schedule =
        switch (job.getIntervalUnit()) {
          case MINUTES ->
              SimpleScheduleBuilder.simpleSchedule().withIntervalInMinutes(value).repeatForever();
          case HOURS ->
              SimpleScheduleBuilder.simpleSchedule().withIntervalInHours(value).repeatForever();
          case DAYS ->
              SimpleScheduleBuilder.simpleSchedule()
                  .withIntervalInHours(value * 24)
                  .repeatForever();
        };
    return builder.withSchedule(schedule).build();
  }

  private JobKey jobKey(Long jobId) {
    return JobKey.jobKey("job-" + jobId, "migration");
  }
}
