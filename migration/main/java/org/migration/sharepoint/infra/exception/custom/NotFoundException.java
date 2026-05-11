/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.infra.exception.custom;

import org.migration.sharepoint.infra.exception.ErrorCode;
import org.migration.sharepoint.infra.exception.base.AppException;

public class NotFoundException extends AppException {
    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}