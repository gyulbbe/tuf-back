package io.github.gyulbbe.user.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.UserDetailDto;
import io.github.gyulbbe.user.dto.UserDto;
import io.github.gyulbbe.user.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RequestMapping("/user")
@AllArgsConstructor
@RestController
public class UserController {

    private final UserService userService;

    @GetMapping("/get/{userId}")
    public ResponseEntity<UserDetailDto> getUserDetail(@Valid @PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserDetail(userId));
    }

    @PostMapping("/insert")
    public ResponseEntity<ResponseDto<Void>> insertUser(@Valid @RequestBody UserDto userDto) {
        return ResponseEntity.ok(userService.insertUser(userDto));
    }

    @PostMapping("/insert-list")
    public ResponseEntity<ResponseDto<Void>> insertUserList(@Valid @RequestBody List<UserDto> userList) {
        return ResponseEntity.ok(userService.insertUserList(userList));
    }
}
