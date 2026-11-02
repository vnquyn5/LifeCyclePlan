package com.lifestyleplanner.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.validation.FieldError;

import org.springframework.web.bind.MethodArgumentNotValidException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.lifestyleplanner.auth.exception.AuthErrorCode;
import com.lifestyleplanner.auth.exception.AuthException;

import com.lifestyleplanner.common.correlation.CorrelationIdConstants;
import com.lifestyleplanner.common.response.ApiErrorResponse;


@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<ApiErrorResponse>
    handleValidationException(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> fieldErrors =
                new LinkedHashMap<>();


        for (
                FieldError error :
                ex.getBindingResult()
                        .getFieldErrors()
        ) {

            fieldErrors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }


        ApiErrorResponse response =
                new ApiErrorResponse(

                        "VALIDATION_ERROR",

                        "Dữ liệu yêu cầu không hợp lệ",

                        fieldErrors,

                        getCorrelationId(),

                        Instant.now()
                );


        return ResponseEntity
                .badRequest()
                .body(response);
    }


    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse>
    handleAuthException(
            AuthException ex
    ) {

        AuthErrorCode errorCode =
                ex.getErrorCode();


        ApiErrorResponse response =
                new ApiErrorResponse(

                        errorCode.getCode(),

                        errorCode.getMessage(),

                        Map.of(),

                        getCorrelationId(),

                        Instant.now()
                );


        return ResponseEntity
                .status(
                        errorCode.getHttpStatus()
                )
                .body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse>
    handleUnexpectedException(
            Exception ex
    ) {

        ApiErrorResponse response =
                new ApiErrorResponse(

                        "INTERNAL_SERVER_ERROR",

                        "Đã xảy ra lỗi hệ thống",

                        Map.of(),

                        getCorrelationId(),

                        Instant.now()
                );


        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(response);
    }


    /**
     * Lấy Correlation ID hiện tại từ MDC.
     */
    private String getCorrelationId() {

        return MDC.get(
                CorrelationIdConstants.MDC_KEY
        );
    }
}