/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.writer;

import java.util.List;

/**
 * Descreve um tipo nativo de banco de dados. Tipos sem parâmetros têm
 * {@code params} vazio. Tipos paramétricos (ex: VARCHAR, DECIMAL) têm um
 * {@code ParamSpec} por posição de parâmetro.
 */
public record NativeTypeDefinition(String name, List<ParamSpec> params) {

    public record ParamSpec(String label, int min, int max) {}
}
