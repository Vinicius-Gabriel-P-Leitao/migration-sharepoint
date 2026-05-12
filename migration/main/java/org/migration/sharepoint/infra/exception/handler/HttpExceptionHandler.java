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
 * Manipulador global de exceções da API. Centraliza o tratamento de erros e garante que as
 * respostas sigam o padrão {@link DataObjectError}.
 */
@Slf4j
@RestControllerAdvice
public class HttpExceptionHandler {

  /** Trata exceções personalizadas da aplicação que estendem {@link AppException}. */
  @ExceptionHandler(AppException.class)
  public ResponseEntity<@NonNull DataObjectError> handleAppException(AppException appException) {
    log.warn("Exceção de negócio: {} - {}", appException.getErrorCode(), appException.getMessage());
    return buildErrorResponse(
        appException.getMessage(), appException.getErrorCode().getHttpStatus());
  }

  /** Trata erros de validação de campos enviados nas requisições (Bean Validation). */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<@NonNull DataObjectError> handleValidationExceptions(
      MethodArgumentNotValidException exception) {
    Map<String, String> errors = new HashMap<>();

    exception
        .getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    log.warn("Erro de validação em {} campos: {}", errors.size(), errors);

    HttpServletRequest request =
        ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    String traceId = MDC.get("requestId");

    DataObjectError error =
        DataObjectError.builder()
            .timestamp(new Date())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
            .code("VALIDATION_ERROR")
            .message("Erro de validação nos campos informados: " + errors.toString())
            .path(request.getRequestURI())
            .traceId(traceId)
            .build();
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  /** Trata falhas de autenticação (Usuário/Senha incorretos). */
  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<@NonNull DataObjectError> handleBadCredentials(
      BadCredentialsException exception) {
    log.info("Tentativa de login com credenciais inválidas.");
    return buildErrorResponse("Usuário ou senha inválidos", HttpStatus.UNAUTHORIZED);
  }

  /** Trata erro de usuário não encontrado. */
  @ExceptionHandler(UsernameNotFoundException.class)
  public ResponseEntity<@NonNull DataObjectError> handleUsernameNotFound(
      UsernameNotFoundException exception) {
    log.info("Usuário não encontrado: {}", exception.getMessage());
    return buildErrorResponse(exception.getMessage(), HttpStatus.UNAUTHORIZED);
  }

  /** Trata falhas genéricas de autenticação no nível de Controller. */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<@NonNull DataObjectError> handleAuthenticationException(
      AuthenticationException exception) {
    log.error("Falha de autenticação: {}", exception.getMessage());
    return buildErrorResponse("Acesso não autorizado ou sessão expirada.", HttpStatus.UNAUTHORIZED);
  }

  /** Trata requisições para rotas que não existem (Spring Boot 3.2+). */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<@NonNull DataObjectError> handleNoResourceFound(
      NoResourceFoundException exception) {
    log.warn("Recurso não encontrado: {}", exception.getResourcePath());
    return buildErrorResponse(
        "O recurso solicitado não foi encontrado no servidor", HttpStatus.NOT_FOUND);
  }

  /** Trata requisições para rotas que não possuem manipulador. */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<@NonNull DataObjectError> handleNotFound(
      NoHandlerFoundException exception) {
    log.warn("Rota não encontrada: {}", exception.getRequestURL());
    return buildErrorResponse("O recurso solicitado não foi encontrado", HttpStatus.NOT_FOUND);
  }

  /** Trata cookie obrigatório ausente (ex: refresh_token não enviado como HttpOnly). */
  @ExceptionHandler(MissingRequestCookieException.class)
  public ResponseEntity<@NonNull DataObjectError> handleMissingCookie(
      MissingRequestCookieException exception) {
    log.warn("Cookie obrigatório ausente: {}", exception.getCookieName());
    return buildErrorResponse(
        "Sessão inválida ou expirada. Faça login novamente.", HttpStatus.UNAUTHORIZED);
  }

  /** Trata erros de parâmetros ausentes na requisição. */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<@NonNull DataObjectError> handleMissingParams(
      MissingServletRequestParameterException exception) {
    log.warn("Parâmetro obrigatório ausente: {}", exception.getParameterName());
    return buildErrorResponse(
        "O parâmetro '" + exception.getParameterName() + "' é obrigatório", HttpStatus.BAD_REQUEST);
  }

  /** Trata erros de tipo de argumento inválido (ex: string onde se espera long). */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<@NonNull DataObjectError> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    log.warn(
        "Tipo de argumento inválido para o parâmetro {}: {}",
        exception.getName(),
        exception.getValue());
    return buildErrorResponse(
        "Valor inválido para o parâmetro '" + exception.getName() + "'", HttpStatus.BAD_REQUEST);
  }

  /** Trata o uso de métodos HTTP incorretos. */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<@NonNull DataObjectError> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException exception) {
    log.warn("Método {} não suportado para a rota.", exception.getMethod());
    return buildErrorResponse(
        "Método HTTP não suportado para esta rota", HttpStatus.METHOD_NOT_ALLOWED);
  }

  /** Trata violações de integridade no banco de dados. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<@NonNull DataObjectError> handleDataIntegrity(
      DataIntegrityViolationException exception) {
    log.error(
        "Conflito de integridade de dados: {}", exception.getMostSpecificCause().getMessage());
    return buildErrorResponse("Erro de integridade de dados ou duplicidade", HttpStatus.CONFLICT);
  }

  /** Fallback para qualquer exceção não tratada especificamente (Erro 500). */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<@NonNull DataObjectError> handleGenericException(Exception exception) {
    log.error("ERRO NÃO TRATADO: ", exception); // Loga o stacktrace completo no servidor
    return buildErrorResponse(
        "Ocorreu um erro interno no servidor", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<@NonNull DataObjectError> buildErrorResponse(
      String message, HttpStatus status) {
    HttpServletRequest request = null;
    try {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) request = attributes.getRequest();
    } catch (Exception ignored) {
    }

    String path = request != null ? request.getRequestURI() : "Unknown path";
    String traceId = MDC.get("requestId");

    DataObjectError error =
        DataObjectError.builder()
            .timestamp(new Date())
            .status(status.value())
            .error(status.getReasonPhrase())
            .code(status.name())
            .message(message)
            .path(path)
            .traceId(traceId)
            .build();
    return new ResponseEntity<>(error, status);
  }
}
