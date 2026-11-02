package com.lifestyleplanner.common.response;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema (description = "Phản hồi lỗi API")
public record ApiErrorResponse(
    @Schema (example = "AUTH_INVALID_CREDENTIALS")
    String code,

    @Schema (example = "Email hoặc mật khẩu không hợp lệ")
    String message,

    @Schema (description = "Danh sách các lỗi validation theo field")
    Map<String, String> fieldErrors,

    @Schema (description = "ID dùng để truy vết lỗi request trong log", example = "123e4567-e89b-12d3-a456-426614174000")
    String correlationId,

    @Schema (description = "Thời gian xảy ra lỗi theo chuẩn ISO 8601", example = "2023-10-01T12:34:56Z")
    Instant timestamp
){

}
