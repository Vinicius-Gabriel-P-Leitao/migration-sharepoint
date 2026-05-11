/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.exception.handler;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.migration.sharepoint.infra.exception.DataObjectError;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Date;

/**
 * Controller personalizado para lidar com erros mapeados para /error.
 * Garante que rotas de API (/v1/**) sempre retornem JSON, mesmo para erros de infraestrutura.
 */
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<?> handleError(HttpServletRequest request) {
        String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            httpStatus = HttpStatus.resolve(statusCode);
            if (httpStatus == null) httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // NOTE: Todos os erros não mapeados que chegam aqui (como 404 estritos do boot) retornam JSON.
        // A SPA passará a ter um fallback dedicado (SpaForwardController)

        String displayMessage = switch (httpStatus) {
            case BAD_REQUEST -> "A requisição enviada é inválida ou malformada";
            case NOT_FOUND -> "O recurso solicitado não foi encontrado";
            default -> "Ocorreu um erro ao processar sua solicitação";
        };

        String traceId = MDC.get("requestId");

        DataObjectError error = DataObjectError.builder()
                .timestamp(new Date())
                .status(httpStatus.value())
                .error(httpStatus.getReasonPhrase())
                .code(httpStatus.name())
                .message(displayMessage)
                .path(uri != null
                        ? uri
                        : "Unknown path")
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(error, httpStatus);
    }
}