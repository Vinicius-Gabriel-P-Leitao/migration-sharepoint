/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import org.apache.catalina.Container;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.jspecify.annotations.NonNull;
import org.migration.sharepoint.infra.exception.DataObjectError;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Substitui o ErrorReportValve padrão do Tomcat por uma implementação que
 * retorna JSON. Captura erros que ocorrem antes do Spring (URI malformada,
 * erros de parse no Tomcat), que não chegam ao @RestControllerAdvice nem ao
 * CustomErrorController.
 */
@Configuration
public class TomcatConfig implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    @Override
    public void customize(@NonNull ConfigurableServletWebServerFactory factory) {
        if (factory instanceof TomcatServletWebServerFactory tomcatFactory) {
            tomcatFactory.addContextCustomizers(context -> {
                Container parent = context.getParent();
                if (parent instanceof StandardHost host) {
                    host.setErrorReportValveClass(CustomErrorReportValve.class.getName());
                }
            });
        }
    }

    public static class CustomErrorReportValve extends ErrorReportValve {

        private final ObjectMapper objectMapper = new ObjectMapper();

        public CustomErrorReportValve() {
            super();
        }

        @Override
        protected void report(Request request, Response response, Throwable throwable) {
            int statusCode = response.getStatus();
            if (statusCode < 400 || response.isCommitted()) return;

            try {
                String traceId = UUID.randomUUID().toString();
                String uri = request.getRequestURI() != null ? request.getRequestURI() : "Unknown Path";

                if (!uri.startsWith("/v1/")) {
                    response.setContentType("text/html");
                    response.setCharacterEncoding("UTF-8");
                    response.setStatus(statusCode);

                    // sendRedirect é bloqueado pelo Tomcat em requisições severamente malformadas
                    // (ex: colchetes soltos na URI). Meta-refresh é o único caminho seguro aqui.
                    String html = """
							<html>
							  <head>
							    <meta http-equiv="refresh" content="0;url=/?error_code=%d">
							  </head>
							  <body></body>
							</html>
							""".formatted(statusCode);

                    response.getWriter().write(html);
                    response.getWriter().flush();
                    return;
                }

                String message =
                        switch (statusCode) {
                            case 400 -> "A requisição enviada possui formato ou caracteres inválidos.";
                            case 404 -> "O recurso solicitado não foi encontrado.";
                            default -> "Ocorreu um erro na camada do servidor web.";
                        };

                DataObjectError error = DataObjectError.builder()
                        .timestamp(new Date())
                        .status(statusCode)
                        .error(HttpStatus.valueOf(statusCode).getReasonPhrase())
                        .code("SERVER_HTTP_ERROR")
                        .message(message)
                        .path(uri)
                        .traceId(traceId)
                        .build();

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(error));
                response.getWriter().flush();

            } catch (IOException ignored) {
            }
        }
    }
}
