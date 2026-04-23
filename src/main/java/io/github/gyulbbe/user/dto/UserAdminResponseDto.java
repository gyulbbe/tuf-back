package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserAdminResponseDto {
    private Long id;
    private String userId;
    private String name;
    private String race;
    private String tier;
    private String status;
}
