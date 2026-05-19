package io.github.gyulbbe.map.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.map.dto.MapDto;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MapService {

    private static final String DUPLICATE_MAP_NAME_MESSAGE = "이미 존재하는 맵입니다.";

    private final MapRepository mapRepository;

    public ResponseDto<Void> insertMap(MapDto mapDto) {
        try {
            if (mapRepository.existsByMapName(mapDto.getMapName())) {
                return ResponseDto.fail(DUPLICATE_MAP_NAME_MESSAGE);
            }

            MapEntity entity = MapEntity.builder()
                    .mapName(mapDto.getMapName())
                    .image(mapDto.getMapName())
                    .build();

            mapRepository.saveAndFlush(entity);
            return ResponseDto.success(null);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate map name while inserting legacy map. mapName={}", mapDto == null ? null : mapDto.getMapName(), e);
            return ResponseDto.fail(DUPLICATE_MAP_NAME_MESSAGE);
        } catch (Exception e) {
            log.error("맵 등록 실패", e);
            return ResponseDto.fail("맵 등록에 실패했습니다.");
        }
    }

    public ResponseDto<MapDto> findMapById(Long id) {
        try {
            MapEntity entity = mapRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("맵을 찾을 수 없습니다.");
            }

            MapDto dto = new MapDto();
            dto.setId(entity.getId());
            dto.setMapName(entity.getMapName());
            dto.setImage(entity.getImage());

            return ResponseDto.success(dto);
        } catch (Exception e) {
            log.error("맵 조회 실패", e);
            return ResponseDto.fail("맵 조회에 실패했습니다.");
        }
    }

    public ResponseDto<Void> updateMap(Long id, MapDto mapDto) {
        try {
            MapEntity entity = mapRepository.findById(id)
                    .orElse(null);

            if (entity == null) {
                return ResponseDto.fail("맵을 찾을 수 없습니다.");
            }

            if (mapRepository.existsByMapNameAndIdNot(mapDto.getMapName(), id)) {
                return ResponseDto.fail(DUPLICATE_MAP_NAME_MESSAGE);
            }

            MapEntity updated = MapEntity.builder()
                    .id(entity.getId())
                    .mapName(mapDto.getMapName())
                    .image(mapDto.getMapName())
                    .build();

            mapRepository.saveAndFlush(updated);
            return ResponseDto.success(null);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate map name while updating legacy map. mapId={}, mapName={}", id, mapDto == null ? null : mapDto.getMapName(), e);
            return ResponseDto.fail(DUPLICATE_MAP_NAME_MESSAGE);
        } catch (Exception e) {
            log.error("맵 수정 실패", e);
            return ResponseDto.fail("맵 수정에 실패했습니다.");
        }
    }

    public ResponseDto<List<MapDto>> list() {
        try {
            List<MapEntity> entities = mapRepository.findAll();

            List<MapDto> dtos = entities.stream().map(entity -> {
                MapDto dto = new MapDto();
                dto.setId(entity.getId());
                dto.setMapName(entity.getMapName());
                dto.setImage(entity.getImage());
                return dto;
            }).toList();

            return ResponseDto.success(dtos);
        } catch (Exception e) {
            log.error("맵 목록 조회 실패", e);
            return ResponseDto.fail("맵 목록 조회에 실패했습니다.");
        }
    }
}
