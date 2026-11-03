package com.lifestyleplanner.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.lifestyleplanner.auth.domain.service.AuthDomainService;
import com.lifestyleplanner.auth.dto.request.LoginRequest;
import com.lifestyleplanner.auth.dto.request.RegisterRequest;
import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.exception.AuthException;
import com.lifestyleplanner.user.domain.entity.User;
import com.lifestyleplanner.user.repository.UserRepository;
import com.lifestyleplanner.auth.session.application.AuthSessionService;

class AuthServiceImplTest {

    private static final String EMAIL = "person@example.com";
    private static final String PASSWORD = "synthetic-password-123";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, new AuthDomainService(), mock(AuthSessionService.class));
    }

    @Test
    void registrationPersistsOnlyTheEncodedPasswordAndReturnsSafeUserData() {
        String passwordHash = "$2a$12$synthetic-hash";
        when(userRepository.existsByEmailNormalized(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(passwordHash);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest(EMAIL, PASSWORD, PASSWORD));

        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(passwordHash, savedUser.getValue().getPasswordHash());
        assertNotEquals(PASSWORD, savedUser.getValue().getPasswordHash());
        assertEquals(EMAIL, response.email());
    }

    @Test
    void registrationRejectsDuplicateEmailBeforeEncodingPassword() {
        when(userRepository.existsByEmailNormalized(EMAIL)).thenReturn(true);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.register(new RegisterRequest(EMAIL, PASSWORD, PASSWORD)));

        assertEquals(AuthErrorCode.EMAIL_ALREADY_EXISTS, exception.getErrorCode());
        org.mockito.Mockito.verifyNoInteractions(passwordEncoder);
    }

    @Test
    void loginUsesTheSameInvalidCredentialsErrorForUnknownEmailAndWrongPassword() {
        var unknownEmail = assertThrows(
                AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, PASSWORD)));

        User existingUser = new User(EMAIL, "stored-hash");
        when(userRepository.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(false);
        var wrongPassword = assertThrows(
                AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, PASSWORD)));

        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, unknownEmail.getErrorCode());
        assertEquals(unknownEmail.getErrorCode(), wrongPassword.getErrorCode());
    }

    @Test
    void disabledAccountWithWrongPasswordGetsTheGenericCredentialError() {
        User disabledUser = new User(EMAIL, "stored-hash");
        disabledUser.disable();
        when(userRepository.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(disabledUser));
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(false);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, PASSWORD)));

        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void disabledAccountWithCorrectPasswordRemainsForbidden() {
        User disabledUser = new User(EMAIL, "stored-hash");
        disabledUser.disable();
        when(userRepository.findByEmailNormalized(EMAIL)).thenReturn(Optional.of(disabledUser));
        when(passwordEncoder.matches(PASSWORD, "stored-hash")).thenReturn(true);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authService.login(new LoginRequest(EMAIL, PASSWORD)));

        assertEquals(AuthErrorCode.ACCOUNT_DISABLED, exception.getErrorCode());
    }
}
