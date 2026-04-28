package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserAdminUpdateRequestDto {
    private String userId;
    private String name;
    private String race;
    private String tier;
}
