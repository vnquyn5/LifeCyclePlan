package com.lifestyleplanner.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu làm mới phiên đăng nhập")
public record RefreshTokenRequest(
        @Schema(description = "Refresh token hiện tại; không ghi token này vào log", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken) {
}
