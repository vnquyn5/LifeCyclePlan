package com.lifestyleplanner.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.lifestyleplanner.auth.validation.PasswordMatches;

@PasswordMatches

public record RegisterRequest(
    @Schema(description = "Email dùng để đăng ký và đăng nhập", example = "user@example.com")
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 254, message = "Email không được vượt quá 254 ký tự")
    String email,

    @Schema(description = "Mật khẩu dùng để đăng nhập", example = "Strong@123", format = "password")
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
    String password,

    @Schema(description = "Xác nhận mật khẩu", example = "Strong@123", format = "password")
    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    String confirmPassword
) {
}
