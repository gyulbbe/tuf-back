package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserAdminRoleUpdateRequestDto {
    private String userType;
    private String role;
}
