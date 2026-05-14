/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.controller.auth.dto.AuthenticationResponse;
import org.migration.sharepoint.service.auth.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            AuthenticationResponse.UserResponse profile = authService.validateToken(token);
            setSecurityContext(profile);
        } catch (Exception e) {
            log.debug("Token validation failed, attempting refresh: {}", e.getMessage());
            try {
                AuthenticationResponse.UserSessionResponse newSession = authService.refresh(request, response);

                if (newSession != null) {
                    AuthenticationResponse.UserResponse profile = authService.validateToken(newSession.accessToken());
                    setSecurityContext(profile);

                    response.setHeader("X-New-Access-Token", newSession.accessToken());
                    response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-New-Access-Token");
                }
            } catch (Exception refreshEx) {
                log.debug("Session refresh failed: {}", refreshEx.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void setSecurityContext(AuthenticationResponse.UserResponse profile) {
        if (profile.roles() != null && profile.roles().contains("ROLE_ADMIN")) {
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(profile.profile().username(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
