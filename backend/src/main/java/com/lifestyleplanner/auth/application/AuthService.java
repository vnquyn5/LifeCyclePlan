package com.lifestyleplanner.auth.application;

import com.lifestyleplanner.auth.dto.request.LoginRequest;
import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import com.lifestyleplanner.auth.dto.response.LoginResponse;
import com.lifestyleplanner.auth.dto.response.RegisterResponse;

public  interface AuthService {
    RegisterResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
