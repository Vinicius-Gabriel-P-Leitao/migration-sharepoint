/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.data.model;

import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.CustomFunction;

public record CustomFieldDefinition(
        String column, ColumnType type, String nativeType, CustomFunction function, String staticValue) {}
