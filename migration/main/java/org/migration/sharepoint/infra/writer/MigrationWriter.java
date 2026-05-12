/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer;

import org.migration.sharepoint.data.enums.TargetDb;

import java.util.List;
import java.util.Map;

/**
 * Port: contrato que todos os adapters de banco de destino devem implementar.
 *
 * <p>A operação {@link #write} realiza full replace — trunca o destino e reimporta todos os dados.
 *
 * <p>Convenção para {@code fieldMappings}: adapters SQL validam que nenhum nome de coluna
 * contenha ponto ('.'), pois isso é inválido em SQL. O adapter MongoDB interpreta ponto como
 * separador de caminho de documento aninhado (ex: {@code "metadata.title"}).
 */
public interface MigrationWriter {

    boolean supports(TargetDb targetDb);

    /**
     * Executa full replace: apaga todo o conteúdo do destino e insere {@code rows}.
     *
     * @param connectionString JDBC URL (SQL) ou connection string (MongoDB) com credenciais embutidas
     * @param targetName       nome da tabela (SQL) ou collection (MongoDB)
     * @param rows             linhas já mapeadas — chave = nome da coluna/campo de destino
     */
    void write(String connectionString, String targetName, List<Map<String, Object>> rows);
}
