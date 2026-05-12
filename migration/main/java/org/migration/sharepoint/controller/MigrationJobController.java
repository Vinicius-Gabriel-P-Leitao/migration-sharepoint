/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.migration.sharepoint.controller.dto.JobRequest;
import org.migration.sharepoint.controller.dto.JobResponse;
import org.migration.sharepoint.controller.dto.LogResponse;
import org.migration.sharepoint.core.service.MigrationJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/jobs")
@RequiredArgsConstructor
public class MigrationJobController {

  private final MigrationJobService service;

  @GetMapping
  public List<JobResponse> findAll() {
    return service.findAll();
  }

  @GetMapping("/{id}")
  public JobResponse findById(@PathVariable Long id) {
    return service.findById(id);
  }

  @PostMapping
  public ResponseEntity<JobResponse> create(@RequestBody @Valid JobRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @PutMapping("/{id}")
  public JobResponse update(@PathVariable Long id, @RequestBody @Valid JobRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/run")
  public ResponseEntity<Void> run(@PathVariable Long id) {
    service.runNow(id);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/{id}/logs")
  public List<LogResponse> logs(@PathVariable Long id) {
    return service.findLogs(id);
  }
}
