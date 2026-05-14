/*
 * SPDX-License-Identifier: BSD-3-Clause
 *
 * Copyright (c) 2026 Vinícius Gabriel Pereira Leitão
 * Licensed under the BSD 3-Clause License.
 * See LICENSE file in the project root for full license information.
 */
package org.migration.sharepoint.controller.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthenticationResponse(UserSessionResponse session, UserResponse user) {

    public record UserSessionResponse(
        String accessToken,
        Integer tokenVersion,
        boolean passwordResetRequired
    ) {}

    public record UserResponse(
        UUID id,
        String email,
        boolean active,
        List<String> roles,
        ProfileDto profile
    ) {}

    public record ProfileDto(
        String username,
        String registration,
        String position
    ) {}
}
