/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.dto;

import org.migration.sharepoint.data.enums.ColumnType;

import java.util.List;
import java.util.Map;

public record AdapterTypesResponse(
        Map<ColumnType, String> canonical,
        List<String> nativeTypes
) {}
