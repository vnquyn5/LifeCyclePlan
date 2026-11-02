package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema (description = "Phản hồi thông tin đăng nhập")
public record LoginResponse(
    @Schema (description = "Thông tin người dùng đã đăng nhập")
    AuthUserResponse user,

    @Schema (description = "Thông tin token được cấp cho người dùng sau khi đăng nhập")
    TokenResponse tokens
) {}
