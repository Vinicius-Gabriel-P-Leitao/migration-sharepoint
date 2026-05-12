/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.connection;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.connection.dto.ConnectionRequest;
import org.migration.sharepoint.infra.connection.ConnectionRegistry;
import org.migration.sharepoint.infra.connection.ConnectionRegistry.ConnectionSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "Connections",
    description =
        "Registry em memória de connection strings. "
            + "As conexões podem ser pré-carregadas via variáveis de ambiente (`CONN_URL_*` + `CONN_NAME_*`) "
            + "ou registradas em runtime via esta API. A URL nunca é exposta nas respostas.")
@RestController
@RequestMapping("/v1/connections")
@RequiredArgsConstructor
public class ConnectionController {

  private final ConnectionRegistry registry;

  @Operation(
      summary = "Listar conexões registradas",
      description =
          "Retorna todas as conexões disponíveis no registry (chave + nome). "
              + "A URL da connection string nunca é exposta.")
  @GetMapping
  public List<ConnectionSummary> list() {
    return registry.list();
  }

  @Operation(
      summary = "Registrar nova conexão",
      description =
          "Registra uma connection string em memória. "
              + "A `key` deve conter apenas letras maiúsculas, números e underscores (ex: `MYSQL_PROD`). "
              + "Para conexões permanentes, prefira definir `CONN_URL_{KEY}` e `CONN_NAME_{KEY}` "
              + "como variáveis de ambiente — elas sobrevivem a restarts do servidor.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Conexão registrada"),
    @ApiResponse(responseCode = "400", description = "Payload inválido ou key com formato incorreto"),
    @ApiResponse(responseCode = "409", description = "Key já registrada — use DELETE antes de re-registrar")
  })
  @PostMapping
  public ResponseEntity<Void> register(@RequestBody @Valid ConnectionRequest request) {
    registry.register(request.key(), request.name(), request.url());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @Operation(
      summary = "Remover conexão",
      description =
          "Remove a conexão do registry em memória. Jobs que referenciam esta key falharão na"
              + " próxima execução. Não afeta variáveis de ambiente — na próxima startup a conexão"
              + " será recarregada automaticamente se as env vars ainda estiverem definidas.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Conexão removida"),
    @ApiResponse(responseCode = "404", description = "Key não encontrada")
  })
  @DeleteMapping("/{key}")
  public ResponseEntity<Void> remove(
      @Parameter(description = "Chave da conexão (ex: MYSQL_PROD)") @PathVariable String key) {
    registry.remove(key);
    return ResponseEntity.noContent().build();
  }
}
