/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.data.repository;

import org.migration.sharepoint.data.model.MigrationJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationJobRepository extends JpaRepository<MigrationJob, Long> {}
