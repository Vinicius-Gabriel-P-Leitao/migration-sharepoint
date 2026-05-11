/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.dto;

import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;

import java.time.LocalDateTime;
import java.util.Map;

public record JobResponse(
        Long id,
        String name,
        String siteId,
        String listId,
        Map<String, String> fieldMappings,
        TargetDb targetDb,
        String connectionString,
        String tableName,
        ScheduleType scheduleType,
        Long intervalValue,
        IntervalUnit intervalUnit,
        String cronExpression,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
