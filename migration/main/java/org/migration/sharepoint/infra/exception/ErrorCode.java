/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not Found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Forbidden"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad Request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"),
    PASSWORD_RESET_REQUIRED(HttpStatus.FORBIDDEN, "Troca de senha obrigatória no primeiro acesso"),

    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Job não encontrado"),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Tabela não encontrada no banco de destino"),
    MIGRATION_CONFLICT(HttpStatus.CONFLICT, "Conflito de integridade na migração"),
    GRAPH_API_ERROR(HttpStatus.BAD_GATEWAY, "Erro ao comunicar com a Microsoft Graph API"),
    DB_CONNECTION_ERROR(HttpStatus.BAD_GATEWAY, "Erro de conexão com o banco de dados de destino");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
