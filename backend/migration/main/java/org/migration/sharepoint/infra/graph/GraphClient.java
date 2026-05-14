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
import java.net.URI;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.BadRequestException;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Consome a Microsoft Graph API usando OAuth2 Client Credentials Flow.
 *
 * <p>
 * O token é obtido via POST para o endpoint do tenant e cacheado em memória. É
 * renovado automaticamente 60 segundos antes de expirar.
 *
 * <p>
 * Limite do Graph API para list items: máximo de 5000 por página
 * ({@code $top}).
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

    public List<Map<String, Object>> fetchListItems(
            String siteId, String listId, Set<String> requestedFields, int pageSize) {
        int effectivePageSize = Math.min(pageSize, GRAPH_MAX_PAGE_SIZE);

        List<Map<String, Object>> all = new ArrayList<>();
        String url = buildUrl(siteId, listId, requestedFields, effectivePageSize);
        String context = "lista='%s' site='%s'".formatted(listId, siteId);

        while (url != null) {
            log.debug("Graph API → {}", url);
            GraphResponse response = authenticatedGet(url, GraphResponse.class, context);
            if (response == null || response.value() == null) break;

            response.value().stream()
                    .map(GraphItem::fields)
                    .filter(Objects::nonNull)
                    .forEach(all::add);

            url = response.nextLink();
        }

        log.info("Graph API retornou {} itens — lista={} site={}", all.size(), listId, siteId);
        return all;
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
            TokenResponse tokenResponse = restClient
                    .post()
                    .uri(URI.create(tokenUrl))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_UNAUTHORIZED,
                                "Falha na autenticação Azure — verifique GRAPH_TENANT_ID, GRAPH_CLIENT_ID e GRAPH_CLIENT_SECRET");
                    })
                    .body(TokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new InfrastructureException(
                        ErrorCode.GRAPH_UNAUTHORIZED,
                        "Resposta do endpoint de token inválida — verifique as credenciais Azure");
            }

            cachedToken = tokenResponse.accessToken();
            tokenExpiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn() - TOKEN_REFRESH_BUFFER_SECONDS);
            log.info(
                    "Token da Graph API obtido. Tenant={} | Válido por ~{}s",
                    tenantId,
                    tokenResponse.expiresIn() - TOKEN_REFRESH_BUFFER_SECONDS);

            return cachedToken;

        } catch (ResourceAccessException networkError) {
            throw new InfrastructureException(
                    ErrorCode.GRAPH_TIMEOUT,
                    "Timeout ao obter token de acesso: %s".formatted(networkError.getMessage()));
        }
    }

    private String buildUrl(String siteId, String listId, Set<String> requestedFields, int top) {
        String expand = (requestedFields != null && !requestedFields.isEmpty())
                ? "fields($select=%s)".formatted(String.join(",", requestedFields))
                : "fields";

        return "%s/sites/%s/lists/%s/items?$expand=%s&$top=%d".formatted(baseUrl, siteId, listId, expand, top);
    }

    // -------------------------------------------------------------------------
    // SharePoint URL resolver
    // -------------------------------------------------------------------------

    public record SharePointResolveResult(String siteId, String listId, List<String> columns) {}

    /**
     * Recebe uma URL de lista SharePoint no formato do browser e retorna siteId,
     * listId e colunas disponíveis — tudo em uma única chamada ao cliente.
     *
     * <p>
     * Exemplo de URL aceita: {@code
     * https://tenant.sharepoint.com/sites/MySite/Lists/MyList/AllItems.aspx}
     */
    public SharePointResolveResult resolveSharePointUrl(String sharePointUrl) {
        URI uri;
        try {
            uri = URI.create(sharePointUrl);
        } catch (Exception invalidUrl) {
            throw new BadRequestException(
                    ErrorCode.SHAREPOINT_INVALID_URL, "URL inválida: %s".formatted(sharePointUrl));
        }

        String host = uri.getHost();
        String path = uri.getPath();

        int listsIdx = path.toLowerCase().indexOf("/lists/");
        if (listsIdx == -1) {
            throw new BadRequestException(
                    ErrorCode.SHAREPOINT_INVALID_URL,
                    "URL não contém /Lists/ — forneça a URL completa da lista SharePoint (ex: .../Lists/NomeDaLista/AllItems.aspx)");
        }

        String sitePath = path.substring(0, listsIdx);
        String listName = path.substring(listsIdx + "/lists/".length()).split("/")[0];

        String siteId = fetchSiteId(host, sitePath);
        String listId = fetchListId(siteId, listName);
        List<String> columns = fetchColumnNames(siteId, listId);

        return new SharePointResolveResult(siteId, listId, columns);
    }

    private String fetchSiteId(String host, String sitePath) {
        String url = "%s/sites/%s:%s".formatted(baseUrl, host, sitePath);
        String context = "site host='%s' path='%s'".formatted(host, sitePath);
        SiteResponse response = authenticatedGet(url, SiteResponse.class, context);
        if (response == null || response.id() == null) {
            throw new InfrastructureException(
                    ErrorCode.GRAPH_SITE_OR_LIST_NOT_FOUND, "Site não encontrado para %s".formatted(context));
        }
        return response.id();
    }

    private String fetchListId(String siteId, String listName) {
        String url = "%s/sites/%s/lists/%s".formatted(baseUrl, siteId, listName);
        String context = "lista '%s' no site '%s'".formatted(listName, siteId);
        ListMetaResponse response = authenticatedGet(url, ListMetaResponse.class, context);

        if (response == null || response.id() == null) {
            throw new InfrastructureException(
                    ErrorCode.GRAPH_SITE_OR_LIST_NOT_FOUND, "Lista não encontrada: %s".formatted(context));
        }

        return response.id();
    }

    private List<String> fetchColumnNames(String siteId, String listId) {
        String url = "%s/sites/%s/lists/%s/columns".formatted(baseUrl, siteId, listId);
        String context = "colunas da lista '%s'".formatted(listId);
        ColumnsResponse response = authenticatedGet(url, ColumnsResponse.class, context);

        if (response == null || response.value() == null) return List.of();
        return response.value().stream()
                .filter(column -> !column.hidden())
                .map(ColumnItem::name)
                .sorted()
                .toList();
    }

    private <T> T authenticatedGet(String url, Class<T> type, String context) {
        try {
            return restClient
                    .get()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer %s".formatted(getAccessToken()))
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_UNAUTHORIZED,
                                "Token rejeitado ao acessar %s (HTTP 401)".formatted(context));
                    })
                    .onStatus(status -> status.value() == 403, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_FORBIDDEN,
                                "Sem permissão para acessar %s (HTTP 403)".formatted(context));
                    })
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_SITE_OR_LIST_NOT_FOUND,
                                "%s não encontrado (HTTP 404)".formatted(context));
                    })
                    .onStatus(status -> status.value() == 429, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_RATE_LIMITED,
                                "Rate limit atingido ao acessar %s (HTTP 429)".formatted(context));
                    })
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_API_ERROR, "Erro 4xx ao acessar %s".formatted(context));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new InfrastructureException(
                                ErrorCode.GRAPH_UNAVAILABLE, "Graph API indisponível ao acessar %s".formatted(context));
                    })
                    .body(type);
        } catch (ResourceAccessException networkError) {
            throw new InfrastructureException(
                    ErrorCode.GRAPH_TIMEOUT, "Timeout ao acessar %s: %s".formatted(context, networkError.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // Internal record types
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphResponse(
            List<GraphItem> value,
            @JsonProperty("@odata.nextLink") String nextLink) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GraphItem(Map<String, Object> fields) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiteResponse(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListMetaResponse(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ColumnItem(String name, boolean hidden) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ColumnsResponse(List<ColumnItem> value) {}
}
