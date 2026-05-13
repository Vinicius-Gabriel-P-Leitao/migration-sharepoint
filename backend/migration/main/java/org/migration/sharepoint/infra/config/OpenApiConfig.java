/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("SP Migrator API").version("0.0.1").description("""
				API de gerenciamento de jobs de migração SharePoint → banco de dados.

				---

				**Fluxo básico**

				**1.** `POST /v1/connections`

				Registre a connection string do banco de destino.

				**2.** `POST /v1/sharepoint/resolve`

				Informe a URL da lista SharePoint e receba siteId, listId e colunas disponíveis.

				**3.** `POST /v1/jobs`

				Crie o job de migração referenciando a conexão e os campos mapeados.

				**4.** `POST /v1/jobs/{id}/run`

				Dispare a migração manualmente ou configure um agendamento.

				---
				"""))
                .tags(List.of(
                        new Tag().name("Jobs").description("Criação, edição, exclusão e execução de jobs de migração"),
                        new Tag()
                                .name("Connections")
                                .description(
                                        "Registry em memória de connection strings — as URLs nunca são persistidas no banco"),
                        new Tag()
                                .name("SharePoint")
                                .description(
                                        "Utilitários para descoberta de metadados de listas SharePoint via Graph API"),
                        new Tag()
                                .name("Adapters")
                                .description("Consulta de tipos suportados por cada adapter de banco de destino")));
    }
}
