package io.github.gyulbbe.common.error;

public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public ApiException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return errorCode.getStatus();
    }
}
