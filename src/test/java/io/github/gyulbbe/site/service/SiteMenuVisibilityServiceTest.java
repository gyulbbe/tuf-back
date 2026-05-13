package io.github.gyulbbe.site.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.site.dto.MenuVisibilityItemDto;
import io.github.gyulbbe.site.dto.MenuVisibilityResponseDto;
import io.github.gyulbbe.site.dto.MenuVisibilityUpdateRequestDto;
import io.github.gyulbbe.site.dto.SiteMenuKey;
import io.github.gyulbbe.site.entity.SiteMenuVisibilityEntity;
import io.github.gyulbbe.site.repository.SiteMenuVisibilityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SiteMenuVisibilityService.class)
@EntityScan(basePackageClasses = SiteMenuVisibilityEntity.class)
@EnableJpaRepositories(basePackageClasses = SiteMenuVisibilityRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sitemenuvisibilitydb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class SiteMenuVisibilityServiceTest {

    @Autowired
    private SiteMenuVisibilityService siteMenuVisibilityService;

    @Autowired
    private SiteMenuVisibilityRepository siteMenuVisibilityRepository;

    @Test
    void getMenuVisibility_returnsAllKeysVisibleWhenSettingsDoNotExist() {
        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.getMenuVisibility();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getItems())
                .extracting(MenuVisibilityItemDto::getMenuKey)
                .containsExactlyElementsOf(SiteMenuKey.orderedMenuKeys());
        assertThat(response.getData().getItems())
                .extracting(MenuVisibilityItemDto::getVisible)
                .containsOnly(true);
    }

    @Test
    void getMenuVisibility_reflectsSavedFalseSetting() {
        siteMenuVisibilityRepository.save(SiteMenuVisibilityEntity.create("external.betting", false, 10L));

        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.getMenuVisibility();

        assertThat(visibleOf(response.getData(), "external.betting")).isFalse();
        assertThat(visibleOf(response.getData(), "chat")).isTrue();
    }

    @Test
    void updateMenuVisibility_partiallyUpdatesRequestedKeysAndKeepsExistingValues() {
        siteMenuVisibilityRepository.save(SiteMenuVisibilityEntity.create("chat", false, 10L));
        siteMenuVisibilityRepository.save(SiteMenuVisibilityEntity.create("external.betting", true, 10L));

        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.updateMenuVisibility(
                request(new MenuVisibilityItemDto("external.betting", false)),
                99L
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(visibleOf(response.getData(), "chat")).isFalse();
        assertThat(visibleOf(response.getData(), "external.betting")).isFalse();
        SiteMenuVisibilityEntity updated = siteMenuVisibilityRepository.findById("external.betting").orElseThrow();
        assertThat(updated.getUpdatedBy()).isEqualTo(99L);
    }

    @Test
    void updateMenuVisibility_allowsEmptyItemsAsNoop() {
        siteMenuVisibilityRepository.save(SiteMenuVisibilityEntity.create("chat", false, 10L));

        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.updateMenuVisibility(request(), 99L);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(visibleOf(response.getData(), "chat")).isFalse();
    }

    @Test
    void updateMenuVisibility_rejectsUnknownMenuKey() {
        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.updateMenuVisibility(
                request(new MenuVisibilityItemDto("unknown", true)),
                99L
        );

        assertValidationFailed(response);
    }

    @Test
    void updateMenuVisibility_rejectsReservedMenuKey() {
        ResponseDto<MenuVisibilityResponseDto> adminResponse = siteMenuVisibilityService.updateMenuVisibility(
                request(new MenuVisibilityItemDto("admin", true)),
                99L
        );
        ResponseDto<MenuVisibilityResponseDto> menuVisibilityResponse = siteMenuVisibilityService.updateMenuVisibility(
                request(new MenuVisibilityItemDto("admin.menuVisibility", true)),
                99L
        );

        assertValidationFailed(adminResponse);
        assertValidationFailed(menuVisibilityResponse);
    }

    @Test
    void updateMenuVisibility_rejectsDuplicateMenuKey() {
        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.updateMenuVisibility(
                request(
                        new MenuVisibilityItemDto("chat", true),
                        new MenuVisibilityItemDto("chat", false)
                ),
                99L
        );

        assertValidationFailed(response);
    }

    @Test
    void updateMenuVisibility_rejectsNullVisible() {
        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.updateMenuVisibility(
                request(new MenuVisibilityItemDto("chat", null)),
                99L
        );

        assertValidationFailed(response);
    }

    @Test
    void updateMenuVisibility_rejectsNullItems() {
        MenuVisibilityUpdateRequestDto requestDto = new MenuVisibilityUpdateRequestDto();
        requestDto.setItems(null);

        ResponseDto<MenuVisibilityResponseDto> response = siteMenuVisibilityService.updateMenuVisibility(requestDto, 99L);

        assertValidationFailed(response);
    }

    private MenuVisibilityUpdateRequestDto request(MenuVisibilityItemDto... items) {
        MenuVisibilityUpdateRequestDto requestDto = new MenuVisibilityUpdateRequestDto();
        requestDto.setItems(List.of(items));
        return requestDto;
    }

    private Boolean visibleOf(MenuVisibilityResponseDto responseDto, String menuKey) {
        return responseDto.getItems().stream()
                .filter(item -> menuKey.equals(item.getMenuKey()))
                .findFirst()
                .orElseThrow()
                .getVisible();
    }

    private void assertValidationFailed(ResponseDto<MenuVisibilityResponseDto> response) {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getData()).isNull();
        assertThat(response.getErrorCode()).isEqualTo("VALIDATION_FAILED");
    }
}
