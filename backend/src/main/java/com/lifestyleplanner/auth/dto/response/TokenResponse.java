package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema (description = "Phản hồi thông tin token")
public record TokenResponse(
    @Schema (description = "JWT access token dùng để gọi API cần xác thực", example = "access_token_example")
    String accessToken,
    @Schema (description = "JWT refresh token dùng để lấy access token mới", example = "refresh_token_example")
    String refreshToken,

    @Schema (description = "Loại token", example = "Bearer")
    String tokenType,

    @Schema (description = "Thời gian sống còn của access token tính bằng giây", example = "3600")
    Long expiresIn
) {}
