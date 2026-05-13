/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.data.model;

import lombok.*;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.CustomFunction;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFieldDefinition {
    private String column;
    private ColumnType type;
    private String nativeType;
    private CustomFunction function;
    private String staticValue;
    private boolean primaryKey;
    private boolean uniqueKey;
}
