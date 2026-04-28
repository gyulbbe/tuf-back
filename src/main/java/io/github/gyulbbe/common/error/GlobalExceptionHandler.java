package io.github.gyulbbe.common.error;

import io.github.gyulbbe.common.dto.ResponseDto;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ResponseDto<Void>> handleApiException(ApiException e) {
        return fail(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseDto<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        return fail(ApiErrorCode.VALIDATION_FAILED, fieldErrorMessage(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseDto<Void>> handleBindException(BindException e) {
        return fail(ApiErrorCode.VALIDATION_FAILED, fieldErrorMessage(e.getBindingResult().getFieldError()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseDto<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return fail(ApiErrorCode.VALIDATION_FAILED, "요청 본문이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResponseDto<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        return fail(ApiErrorCode.VALIDATION_FAILED, "필수 요청 파라미터가 누락되었습니다.");
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ResponseDto<Void>> handleValidationException(Exception e) {
        return fail(ApiErrorCode.VALIDATION_FAILED, firstNonBlank(e.getMessage(), ApiErrorCode.VALIDATION_FAILED.getDefaultMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseDto<Void>> handleAuthenticationException(AuthenticationException e) {
        return fail(ApiErrorCode.AUTH_REQUIRED, ApiErrorCode.AUTH_REQUIRED.getDefaultMessage());
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<ResponseDto<Void>> handleAccessDeniedException(Exception e) {
        return fail(ApiErrorCode.AUTH_FORBIDDEN, ApiErrorCode.AUTH_FORBIDDEN.getDefaultMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseDto<Void>> handleException(Exception e) {
        log.error("Unhandled API exception.", e);
        return fail(ApiErrorCode.INTERNAL_ERROR, ApiErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private ResponseEntity<ResponseDto<Void>> fail(ApiErrorCode errorCode, String message) {
        ResponseDto<Void> body = ResponseDto.fail(errorCode.getStatus(), message, errorCode);
        return ResponseEntity.status(body.getStatus()).body(body);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String fieldErrorMessage(FieldError fieldError) {
        if (fieldError == null) {
            return ApiErrorCode.VALIDATION_FAILED.getDefaultMessage();
        }
        return firstNonBlank(fieldError.getDefaultMessage(), ApiErrorCode.VALIDATION_FAILED.getDefaultMessage());
    }
}
