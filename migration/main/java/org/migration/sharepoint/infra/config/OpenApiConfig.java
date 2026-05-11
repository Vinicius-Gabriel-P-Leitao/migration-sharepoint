/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_DESCRIPTION = "API de Autenticação com JWT e UUIDv7";
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String API_TITLE = "Auth API";
    private static final String API_VERSION = "1.0";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(createApiInfo())
                .addSecurityItem(createSecurityRequirement()).components(createComponents());
    }

    private Info createApiInfo() {
        return new Info().title(API_TITLE).version(API_VERSION).description(API_DESCRIPTION);
    }

    private SecurityRequirement createSecurityRequirement() {
        return new SecurityRequirement().addList(SECURITY_SCHEME_NAME);
    }

    private Components createComponents() {
        return new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, createSecurityScheme());
    }

    private SecurityScheme createSecurityScheme() {
        return new SecurityScheme().name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
    }
}