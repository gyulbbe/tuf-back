package io.github.gyulbbe.common.dto;

import io.github.gyulbbe.common.error.ApiErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;

@Data
public class ResponseDto<T> {

    private int status;
    private String message;
    private T data;
    private String errorCode;

    /**
     * 요청처리를 성공했을 때 응답을 생성해서 반환한다.
     * @param <T> 데이터의 타입
     * @param data 응답으로 보내는 데이터
     * @return REST 표준 응답객체
     */
    public static <T> ResponseDto<T> success(T data) {
        ResponseDto<T> dto = new ResponseDto<>();
        dto.setStatus(HttpServletResponse.SC_OK);
        dto.setMessage("success");
        dto.setData(data);
        dto.setErrorCode(null);

        return dto;
    }

    /**
     * 요청처리를 실패했을 때 응답을 생성해서 반환한다.
     * @param <T> 데이터 타입, 데이터가 없기 때문에 void로 설정한다.
     * @param message 오류 메시지
     * @return REST 표준 응답객체
     */
    public static <T> ResponseDto<T> fail(String message) {
        return fail(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message, ApiErrorCode.INTERNAL_ERROR);
    }

    /**
     * 요청처리를 실패했을 때 응답을 생성해서 반환한다.
     * @param <T> 데이터 타입, 데이터가 없기 때문에 void로 설정한다.
     * @param status 상태 번호
     * @param message 오류 메시지
     * @return REST 표준 응답객체
     */
    public static <T> ResponseDto<T> fail(int status, String message) {
        return fail(status, message, ApiErrorCode.fromStatus(status));
    }

    public static <T> ResponseDto<T> fail(int status, String message, ApiErrorCode errorCode) {
        return fail(status, message, errorCode.name());
    }

    public static <T> ResponseDto<T> fail(int status, String message, String errorCode) {
        ResponseDto<T> dto = new ResponseDto<>();
        dto.setStatus(status);
        dto.setMessage(message);
        dto.setData(null);
        dto.setErrorCode(errorCode);

        return dto;
    }
}
