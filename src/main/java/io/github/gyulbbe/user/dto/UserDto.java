package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserDto {
    private String userId;
    private String password;
    private String name;
    private String tier;
    private String race;
    private String photo;
}
