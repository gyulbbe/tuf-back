package io.github.gyulbbe.common.web;

import io.github.gyulbbe.common.dto.ResponseDto;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static <T> ResponseEntity<ResponseDto<T>> respond(ResponseDto<T> responseDto) {
        return ResponseEntity.status(responseDto.getStatus()).body(responseDto);
    }
}
