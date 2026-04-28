package io.github.gyulbbe.site.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.site.dto.MenuVisibilityItemDto;
import io.github.gyulbbe.site.dto.MenuVisibilityResponseDto;
import io.github.gyulbbe.site.dto.MenuVisibilityUpdateRequestDto;
import io.github.gyulbbe.site.dto.SiteMenuKey;
import io.github.gyulbbe.site.entity.SiteMenuVisibilityEntity;
import io.github.gyulbbe.site.repository.SiteMenuVisibilityRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class SiteMenuVisibilityService {

    private final SiteMenuVisibilityRepository siteMenuVisibilityRepository;

    @Transactional(readOnly = true)
    public ResponseDto<MenuVisibilityResponseDto> getMenuVisibility() {
        return ResponseDto.success(buildResponse(loadSupportedSettings()));
    }

    public ResponseDto<MenuVisibilityResponseDto> updateMenuVisibility(
            MenuVisibilityUpdateRequestDto requestDto,
            Long updatedBy
    ) {
        try {
            List<MenuVisibilityItemDto> items = validateRequest(requestDto);
            if (!items.isEmpty()) {
                Map<String, SiteMenuVisibilityEntity> existingSettings = siteMenuVisibilityRepository.findAllById(
                                items.stream().map(MenuVisibilityItemDto::getMenuKey).toList()
                        ).stream()
                        .collect(Collectors.toMap(SiteMenuVisibilityEntity::getMenuKey, Function.identity()));

                List<SiteMenuVisibilityEntity> entities = new ArrayList<>();
                for (MenuVisibilityItemDto item : items) {
                    SiteMenuVisibilityEntity entity = existingSettings.get(item.getMenuKey());
                    if (entity == null) {
                        entity = SiteMenuVisibilityEntity.create(item.getMenuKey(), item.getVisible(), updatedBy);
                    } else {
                        entity.update(item.getVisible(), updatedBy);
                    }
                    entities.add(entity);
                }
                siteMenuVisibilityRepository.saveAll(entities);
            }
            return ResponseDto.success(buildResponse(loadSupportedSettings()));
        } catch (IllegalArgumentException e) {
            return ResponseDto.fail(
                    HttpServletResponse.SC_BAD_REQUEST,
                    e.getMessage(),
                    ApiErrorCode.VALIDATION_FAILED
            );
        }
    }

    private List<SiteMenuVisibilityEntity> loadSupportedSettings() {
        return siteMenuVisibilityRepository.findAllById(SiteMenuKey.orderedMenuKeys());
    }

    private MenuVisibilityResponseDto buildResponse(List<SiteMenuVisibilityEntity> settings) {
        Map<String, SiteMenuVisibilityEntity> settingByMenuKey = new HashMap<>();
        for (SiteMenuVisibilityEntity setting : settings) {
            settingByMenuKey.put(setting.getMenuKey(), setting);
        }

        MenuVisibilityResponseDto responseDto = new MenuVisibilityResponseDto();
        responseDto.setItems(SiteMenuKey.orderedMenuKeys().stream()
                .map(menuKey -> {
                    SiteMenuVisibilityEntity setting = settingByMenuKey.get(menuKey);
                    return new MenuVisibilityItemDto(menuKey, setting == null || setting.isVisible());
                })
                .toList());
        return responseDto;
    }

    private List<MenuVisibilityItemDto> validateRequest(MenuVisibilityUpdateRequestDto requestDto) {
        if (requestDto == null || requestDto.getItems() == null) {
            throw new IllegalArgumentException("items is required.");
        }

        Set<String> seenMenuKeys = new HashSet<>();
        List<MenuVisibilityItemDto> normalizedItems = new ArrayList<>();
        for (MenuVisibilityItemDto item : requestDto.getItems()) {
            if (item == null) {
                throw new IllegalArgumentException("item is required.");
            }

            String menuKey = normalizeMenuKey(item.getMenuKey());
            if (menuKey == null) {
                throw new IllegalArgumentException("menuKey is required.");
            }
            if (SiteMenuKey.isReserved(menuKey)) {
                throw new IllegalArgumentException("menuKey cannot be changed: " + menuKey);
            }
            if (SiteMenuKey.fromMenuKey(menuKey) == null) {
                throw new IllegalArgumentException("Unsupported menuKey: " + menuKey);
            }
            if (item.getVisible() == null) {
                throw new IllegalArgumentException("visible is required.");
            }
            if (!seenMenuKeys.add(menuKey)) {
                throw new IllegalArgumentException("Duplicate menuKey: " + menuKey);
            }

            normalizedItems.add(new MenuVisibilityItemDto(menuKey, item.getVisible()));
        }
        return normalizedItems;
    }

    private String normalizeMenuKey(String menuKey) {
        if (menuKey == null || menuKey.isBlank()) {
            return null;
        }
        return menuKey.trim();
    }
}
