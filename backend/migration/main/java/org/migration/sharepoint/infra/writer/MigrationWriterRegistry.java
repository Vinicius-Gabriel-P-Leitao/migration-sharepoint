/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.custom.InfrastructureException;
import org.springframework.stereotype.Component;

/**
 * Roteador de adapters: dado um {@link TargetDb}, retorna o
 * {@link MigrationWriter} adequado. Adicionar suporte a um novo banco = criar
 * uma classe que implemente {@link MigrationWriter} e declare
 * {@code supports(TargetDb)} para o banco correspondente.
 */
@Component
@RequiredArgsConstructor
public class MigrationWriterRegistry {

    private final List<MigrationWriter> writers;

    public MigrationWriter get(TargetDb targetDb) {
        return writers.stream()
                .filter(migrationWriter -> migrationWriter.supports(targetDb))
                .findFirst()
                .orElseThrow(() -> new InfrastructureException(
                        ErrorCode.TARGET_DB_NOT_SUPPORTED,
                        "Nenhum writer disponível para o banco: %s".formatted(targetDb)));
    }
}
