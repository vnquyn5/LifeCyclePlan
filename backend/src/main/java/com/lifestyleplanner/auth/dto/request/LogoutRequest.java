package com.lifestyleplanner.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu thu hồi phiên đăng nhập hiện tại")
public record LogoutRequest(
        @Schema(description = "Refresh token hiện tại; không ghi token này vào log", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken) {
}
