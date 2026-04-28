package io.github.gyulbbe.map.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.map.dto.MapDto;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MapService {

    private final MapRepository mapRepository;

    public ResponseDto<Void> insertMap(MapDto mapDto) {
        try {
            MapEntity entity = MapEntity.builder()
                    .mapName(mapDto.getMapName())
                    .image(mapDto.getMapName())
                    .build();

            mapRepository.save(entity);
            return ResponseDto.success(null);
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

            MapEntity updated = MapEntity.builder()
                    .id(entity.getId())
                    .mapName(mapDto.getMapName())
                    .image(mapDto.getMapName())
                    .build();

            mapRepository.save(updated);
            return ResponseDto.success(null);
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
