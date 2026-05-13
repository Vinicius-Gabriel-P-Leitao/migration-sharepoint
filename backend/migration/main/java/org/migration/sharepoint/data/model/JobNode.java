/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.data.model;

import java.util.List;
import java.util.Map;

public record JobNode(
        String sharepointUrl,
        String siteId,
        String listId,
        String tableName,
        Map<String, FieldMapping> fieldMappings,
        List<JobNode> children) {}
