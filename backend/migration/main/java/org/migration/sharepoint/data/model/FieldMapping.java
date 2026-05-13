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

/**
 * Mapeamento de campo para o banco de destino.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMapping {
    private String column;
    private ColumnType type;
    private String nativeType;
    private boolean primaryKey;
    private boolean uniqueKey;
    private boolean autoIncrement;

    public FieldMapping(String column, ColumnType type, String nativeType, boolean primaryKey, boolean uniqueKey) {
        this.column = column;
        this.type = type;
        this.nativeType = nativeType;
        this.primaryKey = primaryKey;
        this.uniqueKey = uniqueKey;
        this.autoIncrement = false;
    }
}
