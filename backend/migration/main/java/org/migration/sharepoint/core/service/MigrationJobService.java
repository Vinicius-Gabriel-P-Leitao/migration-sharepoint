/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.job.dto.JobRequest;
import org.migration.sharepoint.controller.job.dto.JobResponse;
import org.migration.sharepoint.controller.job.dto.LogResponse;
import org.migration.sharepoint.data.model.JobNode;
import org.migration.sharepoint.data.model.MigrationJob;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.migration.sharepoint.data.repository.MigrationLogRepository;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MigrationJobService {

    private final MigrationJobRepository jobRepository;
    private final MigrationLogRepository logRepository;
    private final QuartzSchedulerService quartzSchedulerService;
    private final ConnectionRegistry connectionRegistry;

    public List<JobResponse> findAll() {
        return jobRepository.findAll().stream().map(this::toResponse).toList();
    }

    public JobResponse findById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public JobResponse create(JobRequest request) {
        validateScheduleFields(request);
        validateNode(request.migration(), "migration");
        connectionRegistry.resolveUrl(request.connectionKey());
        MigrationJob job = jobRepository.save(fromRequest(request));
        quartzSchedulerService.schedule(job);
        return toResponse(job);
    }

    @Transactional
    public JobResponse update(Long id, JobRequest request) {
        validateScheduleFields(request);
        validateNode(request.migration(), "migration");
        connectionRegistry.resolveUrl(request.connectionKey());
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

    private void validateScheduleFields(JobRequest request) {
        switch (request.scheduleType()) {
            case INTERVAL -> {
                if (request.intervalValue() == null) {
                    throw new BadRequestException(ErrorCode.BAD_REQUEST, "scheduleType INTERVAL requer intervalValue");
                }
                if (request.intervalUnit() == null) {
                    throw new BadRequestException(ErrorCode.BAD_REQUEST, "scheduleType INTERVAL requer intervalUnit");
                }
            }
            case CRON -> {
                if (request.cronExpression() == null || request.cronExpression().isBlank()) {
                    throw new BadRequestException(ErrorCode.BAD_REQUEST, "scheduleType CRON requer cronExpression");
                }
            }
            case MANUAL, CONTINUOUS -> {}
        }
    }

    private void validateNode(JobNode node, String path) {
        if (node.siteId() == null || node.siteId().isBlank()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "%s.siteId não pode ser vazio".formatted(path));
        }
        if (node.listId() == null || node.listId().isBlank()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "%s.listId não pode ser vazio".formatted(path));
        }
        if (node.tableName() == null || node.tableName().isBlank()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "%s.tableName não pode ser vazio".formatted(path));
        }
        if (node.fieldMappings() == null || node.fieldMappings().isEmpty()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "%s.fieldMappings não pode ser vazio".formatted(path));
        }
        if (node.children() != null) {
            for (int nodeIndex = 0; nodeIndex < node.children().size(); nodeIndex++) {
                validateNode(node.children().get(nodeIndex), "%s.children[%d]".formatted(path, nodeIndex));
            }
        }
    }

    private MigrationJob findOrThrow(Long id) {
        return jobRepository
                .findById(id)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.JOB_NOT_FOUND, "Job id=%d não encontrado".formatted(id)));
    }

    private MigrationJob fromRequest(JobRequest request) {
        return MigrationJob.builder()
                .name(request.name())
                .pageSize(request.pageSize())
                .targetDb(request.targetDb())
                .connectionKey(request.connectionKey())
                .scheduleType(request.scheduleType())
                .intervalValue(request.intervalValue())
                .intervalUnit(request.intervalUnit())
                .cronExpression(request.cronExpression())
                .migration(request.migration())
                .build();
    }

    private void applyRequest(MigrationJob job, JobRequest request) {
        job.setName(request.name());
        job.setPageSize(request.pageSize());
        job.setTargetDb(request.targetDb());
        job.setConnectionKey(request.connectionKey());
        job.setScheduleType(request.scheduleType());
        job.setIntervalValue(request.intervalValue());
        job.setIntervalUnit(request.intervalUnit());
        job.setCronExpression(request.cronExpression());
        job.setMigration(request.migration());
    }

    private JobResponse toResponse(MigrationJob job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getPageSize(),
                job.getTargetDb(),
                job.getConnectionKey(),
                job.getScheduleType(),
                job.getIntervalValue(),
                job.getIntervalUnit(),
                job.getCronExpression(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getMigration());
    }
}
