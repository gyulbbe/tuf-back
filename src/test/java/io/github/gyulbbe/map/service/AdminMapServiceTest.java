package io.github.gyulbbe.map.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.home.repository.HomeScheduleMatchRepository;
import io.github.gyulbbe.map.dto.AdminMapPageResponse;
import io.github.gyulbbe.map.dto.AdminMapRequest;
import io.github.gyulbbe.map.dto.AdminMapResponse;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMapServiceTest {

    @Mock
    private MapRepository mapRepository;

    @Mock
    private HomeScheduleMatchRepository homeScheduleMatchRepository;

    @Mock
    private TournamentMatchRepository tournamentMatchRepository;

    @InjectMocks
    private AdminMapService adminMapService;

    @Test
    void listMaps_returnsPagedResponse() {
        when(mapRepository.findAdminMaps(eq("fight"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(map(1L, "Fighting Spirit")), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        ResponseDto<AdminMapPageResponse> response = adminMapService.listMaps(" fight ", -1, 100);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getItems()).hasSize(1);
        assertThat(response.getData().getSize()).isEqualTo(50);
        assertThat(response.getData().getItems().get(0).getMapName()).isEqualTo("Fighting Spirit");
    }

    @Test
    void createMap_savesMapAndReturnsDates() {
        AdminMapRequest request = request("Fighting Spirit", "/maps/fighting-spirit.png");
        when(mapRepository.existsByMapName("Fighting Spirit")).thenReturn(false);
        when(mapRepository.saveAndFlush(any(MapEntity.class))).thenReturn(map(1L, "Fighting Spirit", "/maps/fighting-spirit.png"));

        ResponseDto<AdminMapResponse> response = adminMapService.createMap(request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getMapName()).isEqualTo("Fighting Spirit");
        assertThat(response.getData().getImage()).isEqualTo("/maps/fighting-spirit.png");
        assertThat(response.getData().getRegDate()).isNotNull();
    }

    @Test
    void createMap_returnsConflictWhenMapNameAlreadyExists() {
        AdminMapRequest request = request("Fighting Spirit", "/maps/fighting-spirit.png");
        when(mapRepository.existsByMapName("Fighting Spirit")).thenReturn(true);

        ResponseDto<AdminMapResponse> response = adminMapService.createMap(request);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("이미 존재하는 맵입니다.");
    }

    @Test
    void updateMap_updatesExistingMap() {
        MapEntity map = map(1L, "Old Map");
        when(mapRepository.findById(1L)).thenReturn(Optional.of(map));
        when(mapRepository.existsByMapNameAndIdNot("New Map", 1L)).thenReturn(false);

        ResponseDto<AdminMapResponse> response = adminMapService.updateMap(1L, request("New Map", "/maps/new.png"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getMapName()).isEqualTo("New Map");
        assertThat(response.getData().getImage()).isEqualTo("/maps/new.png");
    }

    @Test
    void updateMap_returnsConflictWhenMapNameAlreadyExists() {
        MapEntity map = map(1L, "Old Map");
        when(mapRepository.findById(1L)).thenReturn(Optional.of(map));
        when(mapRepository.existsByMapNameAndIdNot("Fighting Spirit", 1L)).thenReturn(true);

        ResponseDto<AdminMapResponse> response = adminMapService.updateMap(1L, request("Fighting Spirit", "/maps/fighting-spirit.png"));

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("이미 존재하는 맵입니다.");
    }

    @Test
    void deleteMap_returnsConflictWhenMapIsUsedByScheduleMatch() {
        when(mapRepository.existsById(1L)).thenReturn(true);
        when(homeScheduleMatchRepository.existsByMapId(1L)).thenReturn(true);

        ResponseDto<Void> response = adminMapService.deleteMap(1L);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getMessage()).isEqualTo("일정 대진에서 사용 중인 맵은 삭제할 수 없습니다.");
    }

    @Test
    void deleteMap_deletesUnusedMap() {
        when(mapRepository.existsById(1L)).thenReturn(true);
        when(homeScheduleMatchRepository.existsByMapId(1L)).thenReturn(false);
        when(tournamentMatchRepository.existsByMapId(1L)).thenReturn(false);

        ResponseDto<Void> response = adminMapService.deleteMap(1L);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(mapRepository).deleteById(1L);
    }

    private AdminMapRequest request(String mapName, String image) {
        AdminMapRequest request = new AdminMapRequest();
        request.setMapName(mapName);
        request.setImage(image);
        return request;
    }

    private MapEntity map(Long id, String name) {
        return map(id, name, "/maps/" + id + ".png");
    }

    private MapEntity map(Long id, String name, String image) {
        return MapEntity.builder()
                .id(id)
                .mapName(name)
                .image(image)
                .regDate(LocalDateTime.of(2026, 5, 17, 12, 0))
                .updateDate(LocalDateTime.of(2026, 5, 17, 12, 0))
                .build();
    }
}
