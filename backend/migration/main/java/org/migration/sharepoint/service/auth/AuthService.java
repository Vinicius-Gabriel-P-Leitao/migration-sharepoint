/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.service.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.controller.auth.dto.AuthenticationRequest;
import org.migration.sharepoint.controller.auth.dto.AuthenticationResponse;
import org.migration.sharepoint.controller.auth.dto.FirstChangePasswordRequest;
import org.migration.sharepoint.infra.exception.custom.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${auth.server.url}")
    private String authServerUrl;

    public AuthenticationResponse login(AuthenticationRequest authRequest, HttpServletResponse authResponse) {
        RestClient restClient = restClientBuilder.baseUrl(authServerUrl).build();

        ResponseEntity<AuthenticationResponse> externalResponse = restClient
                .post()
                .uri("/v1/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(authRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    try (InputStream responseBody = clientResponse.getBody()) {
                        TypeReference<Map<String, Object>> mapType = new TypeReference<>() {};
                        Map<String, Object> errorDetails = objectMapper.readValue(responseBody, mapType);

                        String errorMessage = "Credenciais inválidas ou erro no servidor de autenticação externo";
                        if (errorDetails != null && errorDetails.containsKey("message")) {
                            errorMessage = errorDetails.get("message").toString();
                        }

                        throw new BadCredentialsException(errorMessage);
                    } catch (IOException ioException) {
                        log.error("Erro ao processar corpo de resposta de erro do servidor externo", ioException);
                        throw new BadCredentialsException("Falha na comunicação com o servidor de autenticação");
                    }
                })
                .toEntity(AuthenticationResponse.class);

        AuthenticationResponse authData = externalResponse.getBody();
        if (authData == null
                || authData.user() == null
                || !authData.user().roles().contains("ROLE_ADMIN")) {
            throw new ForbiddenException("Acesso negado: O usuário não possui privilégios de administrador");
        }

        normalizeAndAddCookies(externalResponse, authResponse);

        return authData;
    }

    private void normalizeAndAddCookies(ResponseEntity<?> externalResponse, HttpServletResponse authResponse) {
        List<String> setCookies = externalResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            setCookies.forEach(cookie -> {
                // Força Path=/ e mantém HttpOnly/Secure se existirem no original
                String normalized = cookie.replaceAll("(?i)Path=[^;]+", "Path=/");
                if (!normalized.toLowerCase().contains("path=/")) {
                    normalized += "; Path=/";
                }
                // Garante que o refresh_token especificamente seja HttpOnly se o servidor de auth esqueceu
                if (normalized.startsWith("refresh_token=") && !normalized.toLowerCase().contains("httponly")) {
                    normalized += "; HttpOnly";
                }
                authResponse.addHeader(HttpHeaders.SET_COOKIE, normalized);
            });
        }
    }

    public Map<String, String> firstReset(String accessToken, FirstChangePasswordRequest resetRequest) {
        RestClient restClient = restClientBuilder.baseUrl(authServerUrl).build();

        return restClient
                .post()
                .uri("/v1/password/first-change")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(resetRequest)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {});
    }

    public AuthenticationResponse.UserResponse validateToken(String accessToken) {
        RestClient restClient = restClientBuilder.baseUrl(authServerUrl).build();

        return restClient
                .get()
                .uri("/v1/user/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw new BadCredentialsException("Sessão inválida");
                })
                .body(AuthenticationResponse.UserResponse.class);
    }

    public AuthenticationResponse refresh(
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        RestClient restClient = restClientBuilder.baseUrl(authServerUrl).build();

        String refreshTokenCookie = Arrays.stream(
                        Optional.ofNullable(httpRequest.getCookies()).orElse(new Cookie[0]))
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .findFirst()
                .orElse(null);

        if (refreshTokenCookie == null) {
            throw new BadCredentialsException("Nenhum token de atualização encontrado");
        }

        ResponseEntity<AuthenticationResponse> refreshResponse = restClient
                .post()
                .uri("/v1/user/refresh")
                .header(HttpHeaders.COOKIE, refreshTokenCookie)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (clientRequest, clientResponse) -> {
                    throw new BadCredentialsException("Não foi possível atualizar a sessão");
                })
                .toEntity(AuthenticationResponse.class);

        normalizeAndAddCookies(refreshResponse, httpResponse);

        return refreshResponse.getBody();
    }

    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        RestClient restClient = restClientBuilder.baseUrl(authServerUrl).build();

        String refreshTokenCookie = Arrays.stream(
                        Optional.ofNullable(httpRequest.getCookies()).orElse(new Cookie[0]))
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .findFirst()
                .orElse(null);

        if (refreshTokenCookie != null) {
            try {
                restClient
                        .post()
                        .uri("/v1/user/logout")
                        .header(HttpHeaders.COOKIE, refreshTokenCookie)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception exception) {
                log.warn("Falha ao notificar servidor externo sobre logout", exception);
            }
        }

        // Limpa cookie local
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
