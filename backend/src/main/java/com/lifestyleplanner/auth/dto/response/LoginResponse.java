package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema (description = "Phản hồi thông tin đăng nhập")
public record LoginResponse(
    @Valid
    @NotNull
    @Schema (description = "Thông tin người dùng đã đăng nhập", requiredMode = Schema.RequiredMode.REQUIRED)
    AuthUserResponse user,

    @Valid
    @NotNull
    @Schema (description = "Cặp access/refresh token được cấp sau khi đăng nhập", requiredMode = Schema.RequiredMode.REQUIRED)
    TokenResponse tokens
) {}
