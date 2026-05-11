/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.service;

import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.dto.JobRequest;
import org.migration.sharepoint.controller.dto.JobResponse;
import org.migration.sharepoint.controller.dto.LogResponse;
import org.migration.sharepoint.data.model.MigrationJob;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MigrationJobService {

    private final MigrationJobRepository jobRepository;
    private final MigrationLogRepository logRepository;
    private final QuartzSchedulerService quartzSchedulerService;

    public List<JobResponse> findAll() {
        return jobRepository.findAll().stream().map(this::toResponse).toList();
    }

    public JobResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public JobResponse create(JobRequest request) {
        MigrationJob job = jobRepository.save(fromRequest(request));
        quartzSchedulerService.schedule(job);
        return toResponse(job);
    }

    @Transactional
    public JobResponse update(Long id, JobRequest request) {
        MigrationJob job = findOrThrow(id);
        applyRequest(job, request);
        job = jobRepository.save(job);
        quartzSchedulerService.reschedule(job);
        return toResponse(job);
    }

    @Transactional
    public void delete(Long id) {
        MigrationJob job = findOrThrow(id);
        quartzSchedulerService.unschedule(id);
        jobRepository.delete(job);
    }

    public void runNow(Long id) {
        findOrThrow(id);
        quartzSchedulerService.triggerNow(id);
    }

    public List<LogResponse> findLogs(Long id) {
        findOrThrow(id);
        return logRepository.findByJobIdOrderByStartedAtDesc(id).stream()
                .map(log -> new LogResponse(
                        log.getId(),
                        log.getJob().getId(),
                        log.getStatus(),
                        log.getStartedAt(),
                        log.getFinishedAt(),
                        log.getErrorMessage()))
                .toList();
    }

    private MigrationJob findOrThrow(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.JOB_NOT_FOUND,
                        "Job id=%d não encontrado".formatted(id)));
    }

    private MigrationJob fromRequest(JobRequest r) {
        return MigrationJob.builder()
                .name(r.name())
                .siteId(r.siteId())
                .listId(r.listId())
                .fieldMappings(r.fieldMappings())
                .targetDb(r.targetDb())
                .connectionString(r.connectionString())
                .tableName(r.tableName())
                .scheduleType(r.scheduleType())
                .intervalValue(r.intervalValue())
                .intervalUnit(r.intervalUnit())
                .cronExpression(r.cronExpression())
                .build();
    }

    private void applyRequest(MigrationJob job, JobRequest r) {
        job.setName(r.name());
        job.setSiteId(r.siteId());
        job.setListId(r.listId());
        job.setFieldMappings(r.fieldMappings());
        job.setTargetDb(r.targetDb());
        job.setConnectionString(r.connectionString());
        job.setTableName(r.tableName());
        job.setScheduleType(r.scheduleType());
        job.setIntervalValue(r.intervalValue());
        job.setIntervalUnit(r.intervalUnit());
        job.setCronExpression(r.cronExpression());
    }

    private JobResponse toResponse(MigrationJob j) {
        return new JobResponse(
                j.getId(),
                j.getName(),
                j.getSiteId(),
                j.getListId(),
                j.getFieldMappings(),
                j.getTargetDb(),
                j.getConnectionString(),
                j.getTableName(),
                j.getScheduleType(),
                j.getIntervalValue(),
                j.getIntervalUnit(),
                j.getCronExpression(),
                j.getCreatedAt(),
                j.getUpdatedAt());
    }
}
