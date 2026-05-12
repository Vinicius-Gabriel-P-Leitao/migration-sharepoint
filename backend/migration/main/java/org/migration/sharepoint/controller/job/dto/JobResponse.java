/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.job.dto;

import java.time.LocalDateTime;
import java.util.Map;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;

public record JobResponse(
    Long id,
    String name,
    String siteId,
    String listId,
    Integer pageSize,
    Map<String, FieldMapping> fieldMappings,
    TargetDb targetDb,
    String connectionKey,
    String tableName,
    ScheduleType scheduleType,
    Long intervalValue,
    IntervalUnit intervalUnit,
    String cronExpression,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
