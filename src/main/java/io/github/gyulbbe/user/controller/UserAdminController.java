package io.github.gyulbbe.user.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.UserAdminCreateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminResponseDto;
import io.github.gyulbbe.user.dto.UserAdminStatusUpdateRequestDto;
import io.github.gyulbbe.user.dto.UserAdminUpdateRequestDto;
import io.github.gyulbbe.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/admin")
public class UserAdminController {

    private final UserService userService;

    @GetMapping("/list")
    public ResponseEntity<ResponseDto<List<UserAdminResponseDto>>> listUsers(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "ALL") String status
    ) {
        return toResponseEntity(userService.searchAdminUsers(keyword, status));
    }

    @PostMapping
    public ResponseEntity<ResponseDto<UserAdminResponseDto>> createUser(@RequestBody UserAdminCreateRequestDto requestDto) {
        return toResponseEntity(userService.createAdminUser(requestDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDto<UserAdminResponseDto>> updateUser(
            @PathVariable Long id,
            @RequestBody UserAdminUpdateRequestDto requestDto
    ) {
        return toResponseEntity(userService.updateAdminUser(id, requestDto));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ResponseDto<UserAdminResponseDto>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody UserAdminStatusUpdateRequestDto requestDto
    ) {
        return toResponseEntity(userService.updateAdminUserStatus(id, requestDto));
    }

    private <T> ResponseEntity<ResponseDto<T>> toResponseEntity(ResponseDto<T> responseDto) {
        return ResponseEntity.status(responseDto.getStatus()).body(responseDto);
    }
}
