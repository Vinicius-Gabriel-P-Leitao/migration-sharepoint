/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.data.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.*;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.converter.MapToJsonConverter;

@Entity
@Table(name = "migration_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String siteId;

  @Column(nullable = false)
  private String listId;

  // Itens retornados por página na Graph API (1–5000)
  @Column(nullable = false)
  private Integer pageSize;

  // {"SpField": {"column": "db_col", "type": CANONICAL, "nativeType": "NATIVE"}}
  @Convert(converter = MapToJsonConverter.class)
  @Column(columnDefinition = "TEXT", nullable = false)
  private Map<String, FieldMapping> fieldMappings;

  // Target database
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TargetDb targetDb;

  // Chave que referencia a connection string no ConnectionRegistry (nunca a URL em si)
  @Column(nullable = false)
  private String connectionKey;

  @Column(nullable = false)
  private String tableName;

  // Scheduling
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ScheduleType scheduleType;

  private Long intervalValue;

  @Enumerated(EnumType.STRING)
  private IntervalUnit intervalUnit;

  private String cronExpression;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
