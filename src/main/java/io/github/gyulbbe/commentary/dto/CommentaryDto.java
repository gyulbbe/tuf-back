package io.github.gyulbbe.commentary.dto;

import lombok.Data;

@Data
public class CommentaryDto {
    private Long id;
    private Long matchInfoId;
    private String type;
    private String matchSummary;
}
