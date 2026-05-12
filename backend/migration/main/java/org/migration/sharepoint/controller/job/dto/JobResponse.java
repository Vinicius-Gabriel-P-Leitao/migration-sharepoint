/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.job.dto;

import java.time.LocalDateTime;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.JobNode;

public record JobResponse(
        Long id,
        String name,
        Integer pageSize,
        TargetDb targetDb,
        String connectionKey,
        ScheduleType scheduleType,
        Long intervalValue,
        IntervalUnit intervalUnit,
        String cronExpression,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        JobNode migration) {}
