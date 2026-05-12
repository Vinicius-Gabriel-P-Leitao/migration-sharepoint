/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.migration.sharepoint.data.model.JobNode;
import tools.jackson.databind.ObjectMapper;

@Converter
public class JobNodeConverter implements AttributeConverter<JobNode, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JobNode attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception conversionException) {
            return null;
        }
    }

    @Override
    public JobNode convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MAPPER.readValue(dbData, JobNode.class);
        } catch (Exception conversionException) {
            return null;
        }
    }
}
