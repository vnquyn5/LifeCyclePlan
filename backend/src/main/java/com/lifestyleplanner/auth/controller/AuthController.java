package com.lifestyleplanner.auth.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.validation.Valid;

import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import com.lifestyleplanner.auth.dto.response.RegisterResponse;
import com.lifestyleplanner.auth.application.AuthService;
import com.lifestyleplanner.auth.dto.request.LoginRequest;
import com.lifestyleplanner.auth.dto.response.LoginResponse;
import com.lifestyleplanner.auth.dto.request.RefreshTokenRequest;
import com.lifestyleplanner.auth.dto.response.RefreshResponse;
import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.exception.AuthException;
import com.lifestyleplanner.auth.session.application.AuthSessionService;
import org.springframework.beans.factory.annotation.Autowired;

@RestController 
@RequestMapping ("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthSessionService authSessionService;

    @Autowired
    public AuthController(AuthService authService, AuthSessionService authSessionService){
        this.authService = authService;
        this.authSessionService = authSessionService;
    }

    public AuthController(AuthService authService) {
        this(authService, null);
    }
    @Operation (summary = "Đăng ký tài khoản", 
                description = "Đăng ký tài khoản mới với email và mật khẩu")
    
    @ApiResponses ({
        @ApiResponse (responseCode = "201", description = "Tài khoản được tạo thành công"),
        @ApiResponse (responseCode = "400", description = "Yêu cầu không hợp lệ"),
        @ApiResponse (responseCode = "409", description = "Email đã tồn tại"),
        @ApiResponse (responseCode = "429", description = "Có quá nhiều yêu cầu đăng ký"),
        @ApiResponse (responseCode = "500", description = "Lỗi hệ thống")
    })

    @PostMapping ("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

    @Operation (summary = "Đăng nhập", 
                description = "Đăng nhập với email và mật khẩu")

    @ApiResponses ({
        @ApiResponse (responseCode = "200", description = "Đăng nhập thành công"),
        @ApiResponse (responseCode = "400", description = "Yêu cầu không hợp lệ"),
        @ApiResponse (responseCode = "401", description = "Email hoặc mật khẩu không đúng"),
        @ApiResponse (responseCode = "403", description = "Tài khoản không được phép đăng nhập"),
        @ApiResponse (responseCode = "429", description = "Có quá nhiều yêu cầu đăng nhập"),
        @ApiResponse (responseCode = "500", description = "Lỗi hệ thống")
    })

    @PostMapping ("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Làm mới phiên đăng nhập", description = "Xoay refresh token và cấp access token mới")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Phiên được làm mới thành công"),
        @ApiResponse(responseCode = "400", description = "Yêu cầu không hợp lệ"),
        @ApiResponse(responseCode = "401", description = "Refresh token không hợp lệ hoặc đã hết hạn"),
        @ApiResponse(responseCode = "429", description = "Có quá nhiều yêu cầu làm mới phiên")
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authSessionService.refresh(request.refreshToken())
                .map(tokens -> ResponseEntity.ok(new RefreshResponse(tokens)))
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }
}
