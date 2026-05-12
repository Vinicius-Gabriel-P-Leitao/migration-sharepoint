/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Cria o diretório pai do arquivo SQLite antes que o HikariCP tente abri-lo.
 * BeanFactoryPostProcessor executa antes da instanciação de qualquer bean,
 * garantindo que o diretório exista antes do DataSource ser criado.
 */
@Component
public class SqliteDirectoryInitializer implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:sqlite:")) return;

        String filePath = url.substring("jdbc:sqlite:".length());
        Path dir = Paths.get(filePath).toAbsolutePath().getParent();
        if (dir == null) return;

        try {
            Files.createDirectories(dir);
        } catch (IOException ioException) {
            throw new IllegalStateException(
                    "Não foi possível criar o diretório para o banco SQLite: %s".formatted(dir), ioException);
        }
    }
}
