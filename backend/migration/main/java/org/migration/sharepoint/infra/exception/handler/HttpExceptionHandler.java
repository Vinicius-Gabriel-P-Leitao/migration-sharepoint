/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.exception.handler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.migration.sharepoint.infra.exception.DataObjectError;
import org.migration.sharepoint.infra.exception.base.AppException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Manipulador global de exceções da API. Centraliza o tratamento de erros e
 * garante que as respostas sigam o padrão {@link DataObjectError}.
 */
@Slf4j
@RestControllerAdvice
public class HttpExceptionHandler {

    /**
     * Trata exceções personalizadas da aplicação que estendem {@link AppException}.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<@NonNull DataObjectError> handleAppException(AppException appException) {
        log.warn("Exceção de negócio: {} - {}", appException.getErrorCode(), appException.getMessage());
        return buildErrorResponse(
                appException.getMessage(), appException.getErrorCode().getHttpStatus());
    }

    /**
     * Trata erros de validação de campos enviados nas requisições (Bean
     * Validation).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<@NonNull DataObjectError> handleValidationExceptions(
            MethodArgumentNotValidException validationException) {
        Map<String, String> fieldErrors = new HashMap<>();

        validationException.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                fieldErrors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });

        log.warn("Erro de validação em {} campos: {}", fieldErrors.size(), fieldErrors);

        return buildErrorResponse(
                "Erro de validação nos campos informados: %s".formatted(fieldErrors), HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata falhas de autenticação (Usuário/Senha incorretos).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<@NonNull DataObjectError> handleBadCredentials(
            BadCredentialsException badCredentialsException) {
        String errorMessage = badCredentialsException.getMessage();
        if (errorMessage == null || errorMessage.isBlank() || errorMessage.equalsIgnoreCase("Bad credentials")) {
            errorMessage = "Usuário ou senha inválidos";
        }

        log.info("Tentativa de login com credenciais inválidas: {}", errorMessage);
        return buildErrorResponse(errorMessage, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Trata acesso negado (403 Forbidden).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<@NonNull DataObjectError> handleAccessDenied(AccessDeniedException accessDeniedException) {
        log.warn("Acesso negado: {}", accessDeniedException.getMessage());
        return buildErrorResponse(accessDeniedException.getMessage(), HttpStatus.FORBIDDEN);
    }

    /**
     * Trata erro de usuário não encontrado.
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<@NonNull DataObjectError> handleUsernameNotFound(
            UsernameNotFoundException userNotFoundException) {
        log.info("Usuário não encontrado: {}", userNotFoundException.getMessage());
        return buildErrorResponse(userNotFoundException.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    /**
     * Trata falhas genéricas de autenticação no nível de Controller.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<@NonNull DataObjectError> handleAuthenticationException(
            AuthenticationException authException) {
        log.error("Falha de autenticação: {}", authException.getMessage());
        return buildErrorResponse("Acesso não autorizado ou sessão expirada.", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Trata requisições para rotas que não existem (Spring Boot 3.2+).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<@NonNull DataObjectError> handleNoResourceFound(
            NoResourceFoundException resourceNotFoundException) {
        log.warn("Recurso não encontrado: {}", resourceNotFoundException.getResourcePath());
        return buildErrorResponse("O recurso solicitado não foi encontrado no servidor", HttpStatus.NOT_FOUND);
    }

    /**
     * Trata requisições para rotas que não possuem manipulador.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<@NonNull DataObjectError> handleNotFound(NoHandlerFoundException handlerNotFoundException) {
        log.warn("Rota não encontrada: {}", handlerNotFoundException.getRequestURL());
        return buildErrorResponse("O recurso solicitado não foi encontrado", HttpStatus.NOT_FOUND);
    }

    /**
     * Trata cookie obrigatório ausente (ex: refresh_token não enviado como
     * HttpOnly).
     */
    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<@NonNull DataObjectError> handleMissingCookie(
            MissingRequestCookieException missingCookieException) {
        log.warn("Cookie obrigatório ausente: {}", missingCookieException.getCookieName());
        return buildErrorResponse("Sessão inválida ou expirada. Faça login novamente.", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Trata erros de parâmetros ausentes na requisição.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<@NonNull DataObjectError> handleMissingParams(
            MissingServletRequestParameterException missingParamException) {
        log.warn("Parâmetro obrigatório ausente: {}", missingParamException.getParameterName());
        return buildErrorResponse(
                "O parâmetro '%s' é obrigatório".formatted(missingParamException.getParameterName()),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata erros de tipo de argumento inválido (ex: string onde se espera long).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<@NonNull DataObjectError> handleTypeMismatch(
            MethodArgumentTypeMismatchException typeMismatchException) {
        log.warn(
                "Tipo de argumento inválido para o parâmetro {}: {}",
                typeMismatchException.getName(),
                typeMismatchException.getValue());
        return buildErrorResponse(
                "Valor inválido para o parâmetro '%s'".formatted(typeMismatchException.getName()),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata o uso de métodos HTTP incorretos.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<@NonNull DataObjectError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException methodNotSupportedException) {
        log.warn("Método {} não suportado para a rota.", methodNotSupportedException.getMethod());
        return buildErrorResponse("Método HTTP não suportado para esta rota", HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Trata violações de integridade no banco de dados.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<@NonNull DataObjectError> handleDataIntegrity(
            DataIntegrityViolationException integrityException) {
        log.error(
                "Conflito de integridade de dados: {}",
                integrityException.getMostSpecificCause().getMessage());
        return buildErrorResponse("Erro de integridade de dados ou duplicidade", HttpStatus.CONFLICT);
    }

    /**
     * Trata erros de desserialização JSON (ex: campo faltando em Record, JSON malformado).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<@NonNull DataObjectError> handleMessageNotReadable(
            HttpMessageNotReadableException notReadableException) {
        log.warn("Erro de leitura da requisição HTTP: {}", notReadableException.getMessage());
        return buildErrorResponse(
                "Erro na desserialização do JSON ou corpo da requisição ausente", HttpStatus.BAD_REQUEST);
    }

    /**
     * Fallback para qualquer exceção não tratada especificamente (Erro 500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull DataObjectError> handleGenericException(Exception internalException) {
        log.error("ERRO NÃO TRATADO: ", internalException);
        return buildErrorResponse("Ocorreu um erro interno no servidor", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<@NonNull DataObjectError> buildErrorResponse(String message, HttpStatus status) {
        HttpServletRequest currentRequest = null;

        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                currentRequest = attributes.getRequest();
            }
        } catch (Exception exception) {
            log.trace("Não foi possível obter o HttpServletRequest no buildErrorResponse", exception);
        }

        String requestUri = currentRequest != null ? currentRequest.getRequestURI() : "Unknown path";
        String traceId = MDC.get("requestId");

        DataObjectError errorResponse = DataObjectError.builder()
                .timestamp(new Date())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(status.name())
                .message(message)
                .path(requestUri)
                .traceId(traceId)
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }
}
