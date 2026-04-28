package io.github.gyulbbe.site.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuVisibilityItemDto {
    private String menuKey;
    private Boolean visible;
}
