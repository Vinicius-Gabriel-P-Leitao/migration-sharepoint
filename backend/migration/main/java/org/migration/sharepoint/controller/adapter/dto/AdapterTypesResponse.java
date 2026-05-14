/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.adapter.dto;

import java.util.List;
import java.util.Map;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.infra.writer.NativeTypeDefinition;

public record AdapterTypesResponse(Map<ColumnType, String> canonical, List<NativeTypeDefinition> nativeTypes) {}
