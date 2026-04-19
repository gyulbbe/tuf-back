package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserSearchDto {
    private Long id;
    private String userId;
    private String name;
    private String tier;
    private String race;
    private String photo;
}
