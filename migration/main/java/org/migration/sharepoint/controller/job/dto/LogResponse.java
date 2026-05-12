/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.job.dto;

import java.time.LocalDateTime;
import org.migration.sharepoint.data.enums.JobStatus;

public record LogResponse(
    Long id,
    Long jobId,
    JobStatus status,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    String errorMessage) {}
