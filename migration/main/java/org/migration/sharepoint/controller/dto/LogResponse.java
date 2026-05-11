/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.dto;

import org.migration.sharepoint.data.enums.JobStatus;

import java.time.LocalDateTime;

public record LogResponse(
        Long id,
        Long jobId,
        JobStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String errorMessage
) {}
