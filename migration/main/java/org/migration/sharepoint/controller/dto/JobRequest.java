/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.dto;

import jakarta.validation.constraints.*;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;

import java.util.Map;

public record JobRequest(
        @NotBlank String name,
        @NotBlank String siteId,
        @NotBlank String listId,

        @NotNull @Min(1) @Max(5000) Integer pageSize,

        // {"SharePointField": {"column": "db_col", "type": CANONICAL, "nativeType": "NATIVE"}}
        @NotEmpty Map<String, FieldMapping> fieldMappings,

        @NotNull TargetDb targetDb,
        @NotBlank String connectionString,
        @NotBlank String tableName,

        @NotNull ScheduleType scheduleType,
        Long intervalValue,
        IntervalUnit intervalUnit,
        String cronExpression
) {}
