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
import java.util.ArrayList;
import java.util.List;
import org.migration.sharepoint.data.model.JobNode;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Converter
public class JobNodeListConverter implements AttributeConverter<List<JobNode>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<JobNode>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<JobNode> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception conversionException) {
            return null;
        }
    }

    @Override
    public List<JobNode> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception conversionException) {
            return new ArrayList<>();
        }
    }
}
