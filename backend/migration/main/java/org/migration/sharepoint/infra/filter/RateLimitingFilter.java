/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.migration.sharepoint.infra.exception.DataObjectError;
import org.migration.sharepoint.infra.util.RequestUtil;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro de Rate Limiting para proteger a API contra abusos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Value("${security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest httpRequest,
            @NonNull HttpServletResponse httpResponse,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        String requestPath = httpRequest.getRequestURI();

        if (rateLimitEnabled) {
            String clientIp = RequestUtil.getClientIP(httpRequest);
            Bucket limitBucket = resolveBucket(clientIp);

            if (!limitBucket.tryConsume(1)) {
                log.warn("Rate limit excedido para o IP: {} na rota: {}", clientIp, requestPath);
                sendRateLimitError(httpRequest, httpResponse);
                return;
            }
        }

        filterChain.doFilter(httpRequest, httpResponse);
    }

    private Bucket resolveBucket(String ipAddress) {
        return bucketCache.computeIfAbsent(ipAddress, this::createNewBucket);
    }

    private Bucket createNewBucket(String ipAddress) {
        Bandwidth rateLimit = Bandwidth.builder()
                .capacity(100)
                .refillGreedy(100, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(rateLimit).build();
    }

    private void sendRateLimitError(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String requestUri = httpRequest.getRequestURI();
        boolean isApiEndpoint = requestUri != null && requestUri.startsWith("/v1/");

        if (!isApiEndpoint) {
            httpResponse.sendRedirect("/?error_code=429");
            return;
        }

        httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);

        DataObjectError errorResponse = DataObjectError.builder()
                .timestamp(new Date())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .code(HttpStatus.TOO_MANY_REQUESTS.name())
                .message("Muitas tentativas de requisição. Por favor, aguarde alguns instantes e tente novamente.")
                .path(requestUri)
                .traceId(MDC.get("requestId"))
                .build();
        
        httpResponse.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
