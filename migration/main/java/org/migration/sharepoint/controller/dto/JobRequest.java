/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;

import java.util.Map;

public record JobRequest(
        @NotBlank String name,
        @NotBlank String siteId,
        @NotBlank String listId,

        // {"SharePointField": "db_column"}
        @NotEmpty Map<String, String> fieldMappings,

        @NotNull TargetDb targetDb,
        @NotBlank String connectionString,
        @NotBlank String tableName,

        @NotNull ScheduleType scheduleType,
        Long intervalValue,
        IntervalUnit intervalUnit,
        String cronExpression
) {}
