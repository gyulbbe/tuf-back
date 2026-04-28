package io.github.gyulbbe.site.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuVisibilityResponseDto {
    private List<MenuVisibilityItemDto> items = new ArrayList<>();
}
