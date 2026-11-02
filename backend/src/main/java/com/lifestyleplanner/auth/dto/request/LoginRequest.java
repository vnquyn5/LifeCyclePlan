package com.lifestyleplanner.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema (description = "Yêu cầu đăng nhập")
public record LoginRequest (
    @Schema(description = "Email dùng để đăng ký và đăng nhập", example = "user@example.com")
    @NotBlank (message = "Email không được để trống")
    @Email (message = "Email không đúng định dạng")
    @Size (max = 254, message = "Email không được vượt quá 254 ký tự")
    String email,

    @Schema (description = "Mật khẩu dùng để đăng nhập", example = "Strong@123", format = "password")
    @NotBlank (message = "Mật khẩu không được để trống")
    String password
) {   
}
