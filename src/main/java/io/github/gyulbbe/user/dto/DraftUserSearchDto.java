package io.github.gyulbbe.user.dto;

import lombok.Data;

@Data
public class DraftUserSearchDto {
    private Long id;
    private String userId;
    private String tier;
    private String race;
}
