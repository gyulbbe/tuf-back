package io.github.gyulbbe.site.dto;

import lombok.Data;

import java.util.List;

@Data
public class MenuVisibilityUpdateRequestDto {
    private List<MenuVisibilityItemDto> items;
}
