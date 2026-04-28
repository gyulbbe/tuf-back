package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserAdminCreateRequestDto {
    private String userId;
    private String password;
    private String name;
    private String race;
    private String tier;
}
