/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.exception.base;

import lombok.Getter;
import org.migration.sharepoint.infra.exception.ErrorCode;

@Getter
public abstract class AppException extends RuntimeException {
  private final ErrorCode errorCode;

  protected AppException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
