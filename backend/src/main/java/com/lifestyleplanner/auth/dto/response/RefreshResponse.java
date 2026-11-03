package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Phản hồi làm mới phiên đăng nhập")
public record RefreshResponse(
        @Valid
        @NotNull
        @Schema(description = "Cặp token của phiên sau khi làm mới", requiredMode = Schema.RequiredMode.REQUIRED)
        TokenResponse tokens) {
}
