package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema (description = "Phản hồi thông tin token")
public record TokenResponse(
    @Schema (description = "JWT access token dùng để gọi API cần xác thực; không ghi token này vào log", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank
    String accessToken,
    @Schema (description = "Refresh token dùng để làm mới phiên; không ghi token này vào log", requiredMode = Schema.RequiredMode.REQUIRED, format = "password")
    @NotBlank
    String refreshToken,

    @Schema (description = "Loại token", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Pattern(regexp = "Bearer")
    String tokenType,

    @Schema (description = "Thời gian sống còn của access token tính bằng giây", example = "900", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Min(1)
    Long expiresIn
) {}
