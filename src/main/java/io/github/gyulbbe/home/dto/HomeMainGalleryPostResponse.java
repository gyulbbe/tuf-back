package io.github.gyulbbe.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeMainGalleryPostResponse {
    private Long id;
    private String title;
    private String summaryText;
    private String authorUserId;
    private LocalDateTime regDate;
}
