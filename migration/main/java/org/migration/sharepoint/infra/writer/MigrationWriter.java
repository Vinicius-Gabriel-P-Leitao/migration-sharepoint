/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer;

import java.util.List;
import java.util.Map;
import org.migration.sharepoint.data.enums.ColumnType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.data.model.FieldMapping;

public interface MigrationWriter {

  boolean supports(TargetDb targetDb);

  /**
   * Executa full replace: apaga todo o conteúdo do destino e insere {@code rows}.
   *
   * @param connectionKey chave registrada no ConnectionRegistry
   * @param targetName nome da tabela (SQL) ou collection (MongoDB)
   * @param rows linhas já mapeadas — chave = nome da coluna de destino
   * @param columnTypes mapa de coluna de destino → FieldMapping com tipo declarado
   */
  void write(
      String connectionKey,
      String targetName,
      List<Map<String, Object>> rows,
      Map<String, FieldMapping> columnTypes);

  /** Tipos nativos suportados por este adapter (ex: "VARCHAR", "BIGINT", "TEXT"). */
  List<String> nativeTypes();

  /** Mapeamento de tipos canônicos para os tipos nativos deste adapter. */
  Map<ColumnType, String> canonicalMapping();
}
