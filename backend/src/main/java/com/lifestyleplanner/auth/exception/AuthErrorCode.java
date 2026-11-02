package com.lifestyleplanner.auth.exception;

import org.springframework.http.HttpStatus;

public enum AuthErrorCode {

    EMAIL_ALREADY_EXISTS("Email already exists", HttpStatus.CONFLICT, "Email đã tồn tại"),
    PASSWORD_CONFIRM_MISMATCH("AUTH_PASSWORD_CONFIRM_MISMATCH", HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp"),
    INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Thông tin đăng nhập không hợp lệ"),
    RATE_LIMITED("AUTH_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh. Vui lòng thử lại sau."),
    ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa"),
    UNAUTHENTICATED("AUTH_UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "Yêu cầu xác thực"),
    ACCESS_DENIED("AUTH_ACCESS_DENIED",HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiên thao tác này"),
    ACCOUNT_DELETED("AUTH_ACCOUNT_DELETED", HttpStatus.FORBIDDEN, "Tài khoản đã bị xóa");
    
    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    AuthErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
