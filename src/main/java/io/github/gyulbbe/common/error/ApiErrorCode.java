package io.github.gyulbbe.common.error;

import jakarta.servlet.http.HttpServletResponse;

public enum ApiErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpServletResponse.SC_UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_ACCOUNT_INACTIVE(HttpServletResponse.SC_UNAUTHORIZED, "비활성화된 계정입니다."),
    AUTH_REQUIRED(HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_FORBIDDEN(HttpServletResponse.SC_FORBIDDEN, "권한이 없습니다."),
    VALIDATION_FAILED(HttpServletResponse.SC_BAD_REQUEST, "요청값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpServletResponse.SC_NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpServletResponse.SC_CONFLICT, "요청이 현재 상태와 충돌합니다."),
    BUSINESS_RULE_VIOLATION(HttpServletResponse.SC_BAD_REQUEST, "요청을 처리할 수 없습니다."),
    INTERNAL_ERROR(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final int status;
    private final String defaultMessage;

    ApiErrorCode(int status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public int getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public static ApiErrorCode fromStatus(int status) {
        return switch (status) {
            case HttpServletResponse.SC_UNAUTHORIZED -> AUTH_REQUIRED;
            case HttpServletResponse.SC_FORBIDDEN -> AUTH_FORBIDDEN;
            case HttpServletResponse.SC_NOT_FOUND -> RESOURCE_NOT_FOUND;
            case HttpServletResponse.SC_CONFLICT -> CONFLICT;
            case HttpServletResponse.SC_BAD_REQUEST -> VALIDATION_FAILED;
            default -> INTERNAL_ERROR;
        };
    }
}
