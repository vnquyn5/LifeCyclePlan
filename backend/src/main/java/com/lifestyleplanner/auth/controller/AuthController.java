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

@RestController 
@RequestMapping ("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService){
        this.authService = authService;
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
}
