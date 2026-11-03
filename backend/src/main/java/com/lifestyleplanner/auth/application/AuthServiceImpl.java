package com.lifestyleplanner.auth.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifestyleplanner.user.domain.valueobject.Email;
import com.lifestyleplanner.auth.dto.request.LoginRequest;
import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import com.lifestyleplanner.user.repository.UserRepository;
import com.lifestyleplanner.auth.dto.response.RegisterResponse;
import com.lifestyleplanner.auth.domain.service.AuthDomainService;
import com.lifestyleplanner.auth.dto.response.LoginResponse;
import com.lifestyleplanner.auth.dto.response.AuthUserResponse;
import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.exception.AuthException;
import com.lifestyleplanner.user.domain.entity.User;
import com.lifestyleplanner.auth.session.application.AuthSessionService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;


@Service 
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthDomainService authDomainService;
    private final AuthSessionService authSessionService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuthDomainService authDomainService, AuthSessionService authSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authDomainService = authDomainService;
        this.authSessionService = authSessionService;
    }

    @Override 
    @Transactional 
    public RegisterResponse register(RegisterRequest request) {
        Email email = new Email(request.email());
        boolean emailExists = userRepository.existsByEmailNormalized(email.value());
        if (emailExists) {
            throw new AuthException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (!request.password().equals(request.confirmPassword())) {
            throw new AuthException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User newUser = new User(email.value(), passwordHash);

        try {
            User savedUser = userRepository.save(newUser);
            userRepository.flush();
            return new RegisterResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getOnboardingStatus());
            } catch (DataIntegrityViolationException e) {
                throw new AuthException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
            }
        }
        @Override 
        @Transactional 
        public LoginResponse login(LoginRequest request) {
            String normalizedEmail = Email.normalize(request.email());
            User user = userRepository.findByEmailNormalized(normalizedEmail)
            .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_CREDENTIALS));
            boolean passwordValid = passwordEncoder.matches(request.password(), user.getPasswordHash());
            if (!passwordValid) {
                throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
            }
            authDomainService.validateUserCanLogin(user);
            user.updateLastLogin();
            
            AuthUserResponse userResponse = new AuthUserResponse(user.getId(), user.getEmail(), user.getOnboardingStatus());
            return new LoginResponse(userResponse, authSessionService.issue(user));
        }
}

