/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.sharepoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.sharepoint.dto.SharePointResolveRequest;
import org.migration.sharepoint.controller.sharepoint.dto.SharePointResolveResponse;
import org.migration.sharepoint.infra.graph.GraphClient;
import org.migration.sharepoint.infra.graph.GraphClient.SharePointResolveResult;
import org.springframework.web.bind.annotation.*;

@Tag(name = "SharePoint")
@RestController
@RequestMapping("/v1/sharepoint")
@RequiredArgsConstructor
public class SharePointController {

  private final GraphClient graphClient;

  @Operation(
      summary = "Resolver URL de lista SharePoint",
      description =
          """
          Recebe a URL de uma lista SharePoint no formato do browser e retorna os metadados
          necessários para criar um job de migração.

          **Exemplo de URL aceita:**
          `https://tenant.sharepoint.com/sites/MySite/Lists/MyList/AllItems.aspx`

          **O que retorna:**
          - `siteId` — ID do site SharePoint
          - `listId` — ID da lista
          - `columns` — nomes dos campos disponíveis (sem colunas ocultas), prontos para usar em `fieldMappings`
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Metadados resolvidos com sucesso"),
    @ApiResponse(responseCode = "400", description = "URL inválida ou não contém /Lists/"),
    @ApiResponse(responseCode = "401", description = "Credenciais Azure inválidas"),
    @ApiResponse(responseCode = "403", description = "Sem permissão para acessar o site ou lista"),
    @ApiResponse(responseCode = "404", description = "Site ou lista não encontrados")
  })
  @PostMapping("/resolve")
  public SharePointResolveResponse resolve(@RequestBody @Valid SharePointResolveRequest request) {
    SharePointResolveResult result = graphClient.resolveSharePointUrl(request.url());
    return new SharePointResolveResponse(result.siteId(), result.listId(), result.columns());
  }
}
