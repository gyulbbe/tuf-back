package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class UserDetailDto {
    private String userId;
    private String password;
    private String name;
    private String tier;
    private String race;
    private String photo;
    private String battleTag;
    private int win;
    private int lose;
}
