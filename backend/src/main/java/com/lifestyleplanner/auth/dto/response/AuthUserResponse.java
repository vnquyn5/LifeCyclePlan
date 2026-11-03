package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import com.lifestyleplanner.user.domain.enums.OnboardingStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema (description = "Phản hồi thông tin người dùng")
public record AuthUserResponse(
    @NotNull
    @Schema (description = "ID duy nhất của người dùng", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "550e8400-e29b-41d4-a716-446655440000")
    UUID userId,

    @NotNull
    @NotBlank
    @Email
    @Schema (description = "Email của người dùng", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "user@example.com")
    String email,

    @NotNull
    @Schema (description = "Trạng thái onboarding", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "COMPLETED")
    OnboardingStatus onboardingStatus
) {
}
