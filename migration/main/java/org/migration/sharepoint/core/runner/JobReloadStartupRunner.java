/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.core.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.migration.sharepoint.core.service.QuartzSchedulerService;
import org.migration.sharepoint.data.repository.MigrationJobRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobReloadStartupRunner implements ApplicationRunner {

    private final MigrationJobRepository jobRepository;
    private final QuartzSchedulerService quartzSchedulerService;

    @Override
    public void run(ApplicationArguments args) {
        var jobs = jobRepository.findAll();
        int loaded = 0;

        for (var job : jobs) {
            try {
                quartzSchedulerService.schedule(job);
                loaded++;
            } catch (Exception e) {
                log.error("Falha ao recarregar job '{}' (id={}): {}", job.getName(), job.getId(), e.getMessage());
            }
        }

        log.info("{}/{} jobs recarregados no Quartz na inicialização", loaded, jobs.size());
    }
}
