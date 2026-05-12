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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.*;

/**
 * Consome a Microsoft Graph API usando OAuth2 Client Credentials Flow.
 *
 * <p>O token é obtido via POST para o endpoint do tenant e cacheado em memória.
 * É renovado automaticamente 60 segundos antes de expirar.
 *
 * <p>Limite do Graph API para list items: máximo de 5000 por página ({@code $top}).
 */
@Slf4j
@Component
public class GraphClient {

    static final int GRAPH_MAX_PAGE_SIZE = 5000;
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";
    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 60;

    @Value("${graph.base-url}")
    private String baseUrl;

    @Value("${graph.tenant-id}")
    private String tenantId;

    @Value("${graph.client-id}")
    private String clientId;

    @Value("${graph.client-secret}")
    private String clientSecret;

    private final RestClient restClient;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public GraphClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public List<Map<String, Object>> fetchListItems(String siteId, String listId, Set<String> requestedFields, int pageSize) {
        int effectivePageSize = Math.min(pageSize, GRAPH_MAX_PAGE_SIZE);
        List<Map<String, Object>> all = new ArrayList<>();
        String url = buildUrl(siteId, listId, requestedFields, effectivePageSize);

        while (url != null) {
            log.debug("Graph API → {}", url);
            GraphResponse response = fetch(url, siteId, listId);

            if (response == null || response.value() == null) break;

            response.value().stream().map(GraphItem::fields).filter(Objects::nonNull).forEach(all::add);

            url = response.nextLink();
        }

        log.info("Graph API retornou {} itens — lista={} site={}", all.size(), listId, siteId);
        return all;
    }

    private GraphResponse fetch(String url, String siteId, String listId) {
        try {
            return restClient.get()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer %s".formatted(getAccessToken()))
                    .retrieve()
                    .body(GraphResponse.class);

        } catch (HttpClientErrorException httpClientError) {
            throw switch (httpClientError.getStatusCode().value()) {
                case 401 -> new InfrastructureException(ErrorCode.GRAPH_UNAUTHORIZED,
                        "Token da Graph API rejeitado — verifique GRAPH_CLIENT_ID e GRAPH_CLIENT_SECRET (HTTP 401)");
                case 403 -> new InfrastructureException(ErrorCode.GRAPH_FORBIDDEN,
                        "Sem permissão para acessar site='%s' list='%s' — verifique as permissões da App Registration (HTTP 403)".formatted(siteId,
                                listId));
                case 404 -> new InfrastructureException(ErrorCode.GRAPH_SITE_OR_LIST_NOT_FOUND,
                        "Site ou lista não encontrado: site='%s' list='%s' (HTTP 404)".formatted(siteId, listId));
                case 429 -> new InfrastructureException(ErrorCode.GRAPH_RATE_LIMITED,
                        "Rate limit atingido na Graph API — reduza o pageSize ou aumente o intervalo entre execuções (HTTP 429)");
                default -> new InfrastructureException(ErrorCode.GRAPH_API_ERROR,
                        "Erro HTTP %d na Graph API: %s".formatted(httpClientError.getStatusCode().value(),
                                httpClientError.getResponseBodyAsString()));
            };

        } catch (HttpServerErrorException httpServerError) {
            throw new InfrastructureException(ErrorCode.GRAPH_UNAVAILABLE,
                    "Graph API indisponível (HTTP %d): %s".formatted(httpServerError.getStatusCode().value(), httpServerError.getMessage()));

        } catch (ResourceAccessException networkError) {
            throw new InfrastructureException(ErrorCode.GRAPH_TIMEOUT,
                    "Timeout ou falha de rede ao acessar a Graph API: %s".formatted(networkError.getMessage()));

        } catch (InfrastructureException infraError) {
            throw infraError;

        } catch (Exception unexpectedException) {
            throw new InfrastructureException(ErrorCode.GRAPH_API_ERROR,
                    "Erro inesperado ao acessar Graph API para lista='%s' site='%s': %s".formatted(listId, siteId, unexpectedException.getMessage()));
        }
    }

    private synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        log.debug("Obtendo novo token de acesso para tenant={}", tenantId);

        String tokenUrl = "https://login.microsoftonline.com/%s/oauth2/v2.0/token".formatted(tenantId);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", GRAPH_SCOPE);

        try {
            TokenResponse tokenResponse = restClient.post()
                    .uri(URI.create(tokenUrl))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new InfrastructureException(ErrorCode.GRAPH_UNAUTHORIZED,
                        "Resposta do endpoint de token inválida — verifique as credenciais Azure");
            }

            cachedToken = tokenResponse.accessToken();
            tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn() - TOKEN_REFRESH_BUFFER_SECONDS);
            log.info("Token da Graph API obtido. Tenant={} | Válido por ~{}s", tenantId, tokenResponse.expiresIn() - TOKEN_REFRESH_BUFFER_SECONDS);

            return cachedToken;

        } catch (HttpClientErrorException authError) {
            throw new InfrastructureException(ErrorCode.GRAPH_UNAUTHORIZED,
                    "Falha na autenticação Azure (HTTP %d) — verifique GRAPH_TENANT_ID, GRAPH_CLIENT_ID e GRAPH_CLIENT_SECRET".formatted(authError.getStatusCode()
                            .value()));
        } catch (InfrastructureException infraError) {
            throw infraError;
        } catch (Exception tokenException) {
            throw new InfrastructureException(ErrorCode.GRAPH_UNAUTHORIZED,
                    "Erro ao obter token de acesso: %s".formatted(tokenException.getMessage()));
        }
    }

    private String buildUrl(String siteId, String listId, Set<String> requestedFields, int top) {
        String expand = (requestedFields != null && !requestedFields.isEmpty())
                ? "fields($select=" + String.join(",", requestedFields) + ")"
                : "fields";

        return "%s/sites/%s/lists/%s/items?$expand=%s&$top=%d".formatted(baseUrl, siteId, listId, expand, top);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphResponse(List<GraphItem> value, @JsonProperty("@odata.nextLink") String nextLink) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphItem(Map<String, Object> fields) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }
}
