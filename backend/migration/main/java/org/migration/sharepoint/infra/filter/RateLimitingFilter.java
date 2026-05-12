/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.filter;

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
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

  private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;

  @Value("${security.rate-limit.enabled:true}")
  private boolean enabled;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();

    if (enabled) {
      String ip = RequestUtil.getClientIP(request);
      Bucket bucket = resolveBucket(ip);

      if (!bucket.tryConsume(1)) {
        log.warn("Rate limit excedido para o IP: {} na rota: {}", ip, path);
        sendRateLimitErrorResponse(request, response);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private Bucket resolveBucket(String ip) {
    return cache.computeIfAbsent(ip, this::createNewBucket);
  }

  private Bucket createNewBucket(String ip) {
    Bandwidth limit =
        Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build();
    return Bucket.builder().addLimit(limit).build();
  }

  private void sendRateLimitErrorResponse(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    String uri = request.getRequestURI();
    boolean isApiRoute = uri != null && uri.startsWith("/v1/");

    if (!isApiRoute) {
      response.sendRedirect("/?error_code=429");
      return;
    }

    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

    DataObjectError error =
        DataObjectError.builder()
            .timestamp(new Date())
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
            .code(HttpStatus.TOO_MANY_REQUESTS.name())
            .message(
                "Muitas tentativas de requisição. Por favor, aguarde alguns instantes e tente novamente.")
            .path(request.getRequestURI())
            .traceId(MDC.get("traceId"))
            .build();
    response.getWriter().write(objectMapper.writeValueAsString(error));
  }
}
