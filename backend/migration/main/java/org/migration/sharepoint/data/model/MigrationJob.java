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
import lombok.*;
import org.migration.sharepoint.data.enums.IntervalUnit;
import org.migration.sharepoint.data.enums.ScheduleType;
import org.migration.sharepoint.data.enums.TargetDb;
import org.migration.sharepoint.infra.converter.JobNodeConverter;

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
    private Integer pageSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetDb targetDb;

    @Column(nullable = false)
    private String connectionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleType scheduleType;

    private Long intervalValue;

    @Enumerated(EnumType.STRING)
    private IntervalUnit intervalUnit;

    private String cronExpression;

    @Convert(converter = JobNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JobNode migration;

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
