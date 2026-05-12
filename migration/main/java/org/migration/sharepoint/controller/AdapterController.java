/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller;

import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.dto.AdapterTypesResponse;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.writer.MigrationWriter;
import org.migration.sharepoint.infra.writer.MigrationWriterRegistry;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/adapters")
@RequiredArgsConstructor
public class AdapterController {

  private final MigrationWriterRegistry writerRegistry;

  @GetMapping("/{targetDb}/types")
  public AdapterTypesResponse types(@PathVariable TargetDb targetDb) {
    MigrationWriter writer = writerRegistry.get(targetDb);
    return new AdapterTypesResponse(writer.canonicalMapping(), writer.nativeTypes());
  }
}
