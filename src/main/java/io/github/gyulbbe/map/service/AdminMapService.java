package io.github.gyulbbe.map.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.home.repository.HomeScheduleMatchRepository;
import io.github.gyulbbe.map.dto.AdminMapPageResponse;
import io.github.gyulbbe.map.dto.AdminMapRequest;
import io.github.gyulbbe.map.dto.AdminMapResponse;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Locale;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMapService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String MAP_IN_USE_MESSAGE = "일정 대진에서 사용 중인 맵은 삭제할 수 없습니다.";
    private static final String TOURNAMENT_MAP_IN_USE_MESSAGE = "토너먼트 경기에서 사용 중인 맵은 삭제할 수 없습니다.";

    private final MapRepository mapRepository;
    private final HomeScheduleMatchRepository homeScheduleMatchRepository;
    private final TournamentMatchRepository tournamentMatchRepository;

    @Transactional(readOnly = true)
    public ResponseDto<AdminMapPageResponse> listMaps(String keyword, Integer page, Integer size) {
        try {
            int normalizedPage = normalizePage(page);
            int normalizedSize = normalizeSize(size);
            Pageable pageable = PageRequest.of(
                    normalizedPage,
                    normalizedSize,
                    Sort.by(Sort.Order.asc("mapName"), Sort.Order.asc("id"))
            );
            Page<MapEntity> result = mapRepository.findAdminMaps(normalizeKeyword(keyword), pageable);
            return ResponseDto.success(AdminMapPageResponse.builder()
                    .items(result.getContent().stream().map(this::toResponse).toList())
                    .page(normalizedPage)
                    .size(normalizedSize)
                    .totalElements(result.getTotalElements())
                    .totalPages(result.getTotalPages())
                    .hasNext(result.hasNext())
                    .hasPrevious(result.hasPrevious())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to list admin maps. keyword={}", keyword, e);
            return ResponseDto.fail("Failed to list maps.");
        }
    }

    @Transactional
    public ResponseDto<AdminMapResponse> createMap(AdminMapRequest request) {
        try {
            NormalizedMap normalized = normalizeAndValidate(request);
            MapEntity saved = mapRepository.save(MapEntity.builder()
                    .mapName(normalized.mapName())
                    .image(normalized.image())
                    .build());
            return ResponseDto.success(toResponse(saved));
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to create map.", e);
            return ResponseDto.fail("Failed to create map.");
        }
    }

    @Transactional
    public ResponseDto<AdminMapResponse> updateMap(Long mapId, AdminMapRequest request) {
        try {
            if (mapId == null) {
                throw new NoSuchElementException("Map not found.");
            }

            NormalizedMap normalized = normalizeAndValidate(request);
            MapEntity map = mapRepository.findById(mapId)
                    .orElseThrow(() -> new NoSuchElementException("Map not found."));
            map.update(normalized.mapName(), normalized.image());
            return ResponseDto.success(toResponse(map));
        } catch (NoSuchElementException e) {
            return notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to update map. mapId={}", mapId, e);
            return ResponseDto.fail("Failed to update map.");
        }
    }

    @Transactional
    public ResponseDto<Void> deleteMap(Long mapId) {
        try {
            if (mapId == null) {
                throw new NoSuchElementException("Map not found.");
            }
            if (!mapRepository.existsById(mapId)) {
                throw new NoSuchElementException("Map not found.");
            }
            if (homeScheduleMatchRepository.existsByMapId(mapId)) {
                return conflict(MAP_IN_USE_MESSAGE);
            }
            if (tournamentMatchRepository.existsByMapId(mapId)) {
                return conflict(TOURNAMENT_MAP_IN_USE_MESSAGE);
            }

            mapRepository.deleteById(mapId);
            return ResponseDto.success(null);
        } catch (NoSuchElementException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to delete map. mapId={}", mapId, e);
            return ResponseDto.fail("Failed to delete map.");
        }
    }

    private AdminMapResponse toResponse(MapEntity map) {
        return AdminMapResponse.builder()
                .id(map.getId())
                .mapName(map.getMapName())
                .image(map.getImage())
                .regDate(map.getRegDate())
                .updateDate(map.getUpdateDate())
                .build();
    }

    private NormalizedMap normalizeAndValidate(AdminMapRequest request) {
        String mapName = normalizeBlankToNull(request == null ? null : request.getMapName());
        if (mapName == null) {
            throw new IllegalArgumentException("mapName is required.");
        }
        if (mapName.length() > 255) {
            throw new IllegalArgumentException("mapName must be 255 characters or less.");
        }

        String image = normalizeBlankToNull(request == null ? null : request.getImage());
        if (image != null && image.length() > 255) {
            throw new IllegalArgumentException("image must be 255 characters or less.");
        }
        return new NormalizedMap(mapName, image);
    }

    private String normalizeKeyword(String keyword) {
        String normalized = normalizeBlankToNull(keyword);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int normalizePage(Integer page) {
        if (page == null || page < DEFAULT_PAGE) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private <T> ResponseDto<T> validationFailed(String message) {
        return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, message, ApiErrorCode.VALIDATION_FAILED);
    }

    private <T> ResponseDto<T> notFound(String message) {
        return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, message, ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    private <T> ResponseDto<T> conflict(String message) {
        return ResponseDto.fail(HttpServletResponse.SC_CONFLICT, message, ApiErrorCode.CONFLICT);
    }

    private void markRollbackOnly() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }

    private record NormalizedMap(String mapName, String image) {
    }
}
