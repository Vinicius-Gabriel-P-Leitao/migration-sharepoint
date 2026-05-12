/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.connection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConnectionRequest(
    @NotBlank
        @Pattern(
            regexp = "[A-Z0-9_]+",
            message = "key deve conter apenas letras maiúsculas, números e underscores")
        String key,
    @NotBlank String name,
    @NotBlank String url) {}
