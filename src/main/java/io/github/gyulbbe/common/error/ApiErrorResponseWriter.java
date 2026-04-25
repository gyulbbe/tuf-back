package io.github.gyulbbe.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gyulbbe.common.dto.ResponseDto;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class ApiErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ApiErrorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ApiErrorCode errorCode) throws IOException {
        write(response, errorCode, errorCode.getDefaultMessage());
    }

    public static void write(HttpServletResponse response, ApiErrorCode errorCode, String message) throws IOException {
        ResponseDto<Void> body = ResponseDto.fail(errorCode.getStatus(), message, errorCode);
        response.setStatus(errorCode.getStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}
