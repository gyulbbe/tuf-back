package io.github.gyulbbe.user.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.user.dto.DraftUserSearchDto;
import io.github.gyulbbe.user.dto.UserDetailDto;
import io.github.gyulbbe.user.dto.UserDto;
import io.github.gyulbbe.user.dto.UserSearchDto;
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

    @GetMapping("/search")
    public ResponseEntity<ResponseDto<List<UserSearchDto>>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(userService.searchUsers(keyword, limit));
    }

    @GetMapping("/draft-search")
    public ResponseEntity<ResponseDto<List<DraftUserSearchDto>>> searchDraftUsers(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(userService.searchDraftUsers(keyword, limit));
    }

    @PatchMapping("/password/{id}")
    public ResponseEntity<ResponseDto<Void>> updatePassword(@PathVariable Long id, @RequestBody String newPassword) {
        return ResponseEntity.ok(userService.updatePassword(id, newPassword));
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
