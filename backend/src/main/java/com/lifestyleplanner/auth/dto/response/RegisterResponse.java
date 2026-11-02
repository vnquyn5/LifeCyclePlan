package com.lifestyleplanner.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import com.lifestyleplanner.user.domain.enums.OnboardingStatus;

@Schema (description = "Phản hồi xác thực")
public record RegisterResponse (
    @Schema (description = "ID duy nhất của người dùng", 
                example = "550e8400-e29b-41d4-a716-446655440000")
    UUID userId,

    @Schema (description = "Email của tài khoản vừa được tạo",
                example = "user@example.com")
    String email,

    @Schema (description = "Trạng thái onboarding hiện tại của người dùng", 
                example = "NOT_STARTED")
    OnboardingStatus onboardingStatus
){}
