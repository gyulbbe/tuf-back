package io.github.gyulbbe.site.controller;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.site.dto.MenuVisibilityResponseDto;
import io.github.gyulbbe.site.dto.MenuVisibilityUpdateRequestDto;
import io.github.gyulbbe.site.service.SiteMenuVisibilityService;
import io.github.gyulbbe.user.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SiteMenuVisibilityController {

    private final SiteMenuVisibilityService siteMenuVisibilityService;

    @GetMapping("/site/menu-visibility")
    public ResponseEntity<ResponseDto<MenuVisibilityResponseDto>> getMenuVisibility() {
        return toResponseEntity(siteMenuVisibilityService.getMenuVisibility());
    }

    @PutMapping("/admin/menu-visibility")
    public ResponseEntity<ResponseDto<MenuVisibilityResponseDto>> updateMenuVisibility(
            @RequestBody MenuVisibilityUpdateRequestDto requestDto,
            Authentication authentication
    ) {
        return toResponseEntity(siteMenuVisibilityService.updateMenuVisibility(requestDto, resolveUserPk(authentication)));
    }

    private Long resolveUserPk(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails customUserDetails)) {
            return null;
        }
        return customUserDetails.getUserPk();
    }

    private <T> ResponseEntity<ResponseDto<T>> toResponseEntity(ResponseDto<T> responseDto) {
        return ResponseEntity.status(responseDto.getStatus()).body(responseDto);
    }
}
