/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class GraphClient {

    @Value("${graph.base-url}")
    private String baseUrl;

    @Value("${graph.page-size:999}")
    private int pageSize;

    @Value("${graph.token-id}")
    private String tokenId;

    @Value("${graph.client-id}")
    private String clientId;

    @Value("${graph.client-secret}")
    private String clientSecret;

    private final RestClient restClient;

    public GraphClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public List<Map<String, Object>> fetchListItems(String siteId, String listId, Set<String> requestedFields) {
        List<Map<String, Object>> all = new ArrayList<>();
        String url = buildUrl(siteId, listId, requestedFields);

        while (url != null) {
            log.debug("Graph API → {}", url);
            try {
                GraphResponse response = restClient.get()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + tokenId)
                        .header("client-id", clientId)
                        .retrieve()
                        .body(GraphResponse.class);

                if (response == null || response.value() == null) break;

                response.value().stream()
                        .filter(item -> item.fields() != null)
                        .map(GraphItem::fields)
                        .forEach(all::add);

                url = response.nextLink();
            } catch (Exception e) {
                throw new InfrastructureException(ErrorCode.GRAPH_API_ERROR,
                        "Falha ao buscar lista '%s' no site '%s': %s".formatted(listId, siteId, e.getMessage()));
            }
        }

        log.info("Graph API retornou {} itens — lista={} site={}", all.size(), listId, siteId);
        return all;
    }

    private String buildUrl(String siteId, String listId, Set<String> requestedFields) {
        String expand = (requestedFields != null && !requestedFields.isEmpty())
                ? "fields($select=" + String.join(",", requestedFields) + ")"
                : "fields";

        return baseUrl + "/sites/" + siteId + "/lists/" + listId
                + "/items?$expand=" + expand + "&$top=" + pageSize;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphResponse(
            List<GraphItem> value,
            @JsonProperty("@odata.nextLink") String nextLink
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphItem(
            Map<String, Object> fields
    ) {}
}
