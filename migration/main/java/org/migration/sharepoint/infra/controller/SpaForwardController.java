/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2025 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Fallback Controller para rotas da SPA.
 * Substitui o uso de Redirect (302) pelo uso de Forward, mantendo o backend
 * stateless e delegando a responsabilidade de navegação visual ao Frontend (React).
 */
@Controller
public class SpaForwardController {

    @RequestMapping(value = "/{path:[^\\.]*}")
    public String forward() {
        return "forward:/index.html";
    }
}