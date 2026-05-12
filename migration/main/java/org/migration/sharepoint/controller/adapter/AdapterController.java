/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.adapter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.adapter.dto.AdapterTypesResponse;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.MigrationWriterRegistry;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Adapters")
@RestController
@RequestMapping("/v1/adapters")
@RequiredArgsConstructor
public class AdapterController {

  private final MigrationWriterRegistry writerRegistry;

  @Operation(
      summary = "Tipos suportados pelo adapter",
      description =
          """
          Retorna os tipos disponíveis para uso no campo `fieldMappings` de um job.

          - `canonical` — mapa de tipos abstratos (`TEXT`, `NUMBER`, `DECIMAL`, `BOOLEAN`, `DATE`, `DATETIME`) \
          para o tipo nativo equivalente neste banco. Use no campo `type` do fieldMapping.
          - `nativeTypes` — lista de tipos nativos aceitos pelo adapter. Use no campo `nativeType` \
          do fieldMapping para controle fino (ex: `VARCHAR(100)`, `DECIMAL(15,2)`).

          O campo `nativeType` tem precedência sobre `type` quando ambos são informados.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tipos retornados com sucesso"),
    @ApiResponse(responseCode = "400", description = "Banco de destino não suportado")
  })
  @GetMapping("/{targetDb}/types")
  public AdapterTypesResponse types(
      @Parameter(description = "Banco de destino (ex: MYSQL, POSTGRESQL, MONGODB)")
          @PathVariable
          TargetDb targetDb) {
    MigrationWriter writer = writerRegistry.get(targetDb);
    return new AdapterTypesResponse(writer.canonicalMapping(), writer.nativeTypes());
  }
}
