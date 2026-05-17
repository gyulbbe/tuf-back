package io.github.gyulbbe.home.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.home.dto.AdminHomeScheduleCreateRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleDeleteRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleDeleteResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMapSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMatchPlayerRequest;
import io.github.gyulbbe.home.dto.AdminHomeScheduleMatchRequest;
import io.github.gyulbbe.home.dto.AdminHomeSchedulePageResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleProleagueTeamSearchResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleResponse;
import io.github.gyulbbe.home.dto.AdminHomeScheduleUpdateRequest;
import io.github.gyulbbe.home.dto.HomeScheduleMatchPlayerResponse;
import io.github.gyulbbe.home.dto.HomeScheduleMatchResponse;
import io.github.gyulbbe.home.dto.HomeScheduleResponse;
import io.github.gyulbbe.home.entity.HomeScheduleEntity;
import io.github.gyulbbe.home.entity.HomeScheduleMatchEntity;
import io.github.gyulbbe.home.entity.HomeScheduleMatchPlayerEntity;
import io.github.gyulbbe.home.repository.HomeScheduleMatchPlayerRepository;
import io.github.gyulbbe.home.repository.HomeScheduleMatchRepository;
import io.github.gyulbbe.home.repository.HomeScheduleProleagueTeamQueryRepository;
import io.github.gyulbbe.home.repository.HomeScheduleRepository;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeScheduleService {

    private static final int DEFAULT_PUBLIC_LIMIT = 3;
    private static final int MAX_PUBLIC_LIMIT = 20;
    private static final int DEFAULT_ADMIN_PAGE = 0;
    private static final int DEFAULT_ADMIN_SIZE = 20;
    private static final int MAX_ADMIN_SIZE = 50;
    private static final int DEFAULT_MAP_SEARCH_LIMIT = 10;
    private static final int MAX_MAP_SEARCH_LIMIT = 30;
    private static final int DEFAULT_PROLEAGUE_TEAM_SEARCH_LIMIT = 10;
    private static final int MAX_PROLEAGUE_TEAM_SEARCH_LIMIT = 30;
    private static final String STATUS_UPCOMING = "UPCOMING";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final DateTimeFormatter TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> MATCH_FORMATS = Set.of(
            HomeScheduleMatchEntity.FORMAT_1V1,
            HomeScheduleMatchEntity.FORMAT_2V2,
            HomeScheduleMatchEntity.FORMAT_3V3,
            HomeScheduleMatchEntity.FORMAT_ACE,
            HomeScheduleMatchEntity.FORMAT_CUSTOM
    );
    private static final Set<String> PLAYER_RACES = Set.of("ZERG", "TERRAN", "PROTOSS", "RANDOM");

    private final HomeScheduleRepository homeScheduleRepository;
    private final HomeScheduleMatchRepository homeScheduleMatchRepository;
    private final HomeScheduleMatchPlayerRepository homeScheduleMatchPlayerRepository;
    private final HomeScheduleProleagueTeamQueryRepository proleagueTeamQueryRepository;
    private final MapRepository mapRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ResponseDto<List<HomeScheduleResponse>> listPublicSchedules(Integer limit) {
        try {
            return ResponseDto.success(findPublicSchedules(limit));
        } catch (Exception e) {
            log.warn("Failed to list public home schedules.", e);
            return ResponseDto.fail("Failed to list home schedules.");
        }
    }

    @Transactional(readOnly = true)
    public List<HomeScheduleResponse> findPublicSchedules(Integer limit) {
        int normalizedLimit = normalizeLimit(limit, DEFAULT_PUBLIC_LIMIT, MAX_PUBLIC_LIMIT);
        List<HomeScheduleEntity> schedules = homeScheduleRepository.findPublicRepresentativeSchedules(LocalDateTime.now())
                .stream()
                .limit(normalizedLimit)
                .toList();
        ScheduleRelations relations = loadScheduleRelations(schedules, false);
        return schedules.stream()
                .map(schedule -> toPublicResponse(schedule, relations))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseDto<String> getRedirectTarget(Long scheduleId) {
        try {
            if (scheduleId == null) {
                throw new NoSuchElementException("Home schedule not found.");
            }

            HomeScheduleEntity schedule = homeScheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new NoSuchElementException("Home schedule not found."));
            if (schedule.getTargetUrl() == null || schedule.getTargetUrl().isBlank()) {
                throw new NoSuchElementException("Home schedule target URL not found.");
            }

            return ResponseDto.success(schedule.getTargetUrl());
        } catch (NoSuchElementException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to get home schedule redirect target. scheduleId={}", scheduleId, e);
            return ResponseDto.fail("Failed to redirect home schedule.");
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<AdminHomeSchedulePageResponse> listAdminSchedules(
            Integer page,
            Integer size,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate,
            String scheduleGroup
    ) {
        try {
            int normalizedPage = normalizePage(page);
            int normalizedSize = normalizeLimit(size, DEFAULT_ADMIN_SIZE, MAX_ADMIN_SIZE);
            Pageable pageable = PageRequest.of(
                    normalizedPage,
                    normalizedSize,
                    Sort.by(Sort.Order.desc("scheduledAt"), Sort.Order.desc("id"))
            );
            LocalDateTime now = LocalDateTime.now();
            Page<HomeScheduleEntity> result = homeScheduleRepository.findAll(
                    adminSearchSpec(normalizeBlankToNull(keyword), fromDate, toDate, normalizeBlankToNull(scheduleGroup)),
                    pageable
            );
            ScheduleRelations relations = loadScheduleRelations(result.getContent(), false);

            return ResponseDto.success(AdminHomeSchedulePageResponse.builder()
                    .items(result.getContent().stream().map(schedule -> toAdminResponse(schedule, now, relations)).toList())
                    .page(normalizedPage)
                    .size(normalizedSize)
                    .totalElements(result.getTotalElements())
                    .totalPages(result.getTotalPages())
                    .hasNext(result.hasNext())
                    .hasPrevious(result.hasPrevious())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to list admin home schedules.", e);
            return ResponseDto.fail("Failed to list admin home schedules.");
        }
    }

    @Transactional
    public ResponseDto<AdminHomeScheduleResponse> createSchedule(AdminHomeScheduleCreateRequest request) {
        try {
            NormalizedSchedule normalized = normalizeAndValidate(
                    request == null ? null : request.getScheduleGroup(),
                    request == null ? null : request.getTitle(),
                    request == null ? null : request.getDescription(),
                    request == null ? null : request.getScheduledAt(),
                    request == null ? null : request.getTargetUrl(),
                    request == null ? null : request.getLinkType(),
                    request == null ? null : request.getDisplayPriority()
            );
            List<HomeScheduleMatchEntity> matches = normalizeMatches(request == null ? null : request.getMatches());

            HomeScheduleEntity schedule = HomeScheduleEntity.builder()
                    .scheduleGroup(normalized.scheduleGroup())
                    .title(normalized.title())
                    .description(normalized.description())
                    .scheduledAt(normalized.scheduledAt())
                    .targetUrl(normalized.targetUrl())
                    .linkType(normalized.linkType())
                    .displayPriority(normalized.displayPriority())
                    .build();
            schedule.replaceMatches(matches);

            HomeScheduleEntity saved = homeScheduleRepository.save(schedule);
            return ResponseDto.success(toAdminResponse(saved, LocalDateTime.now(), loadScheduleRelations(List.of(saved), true)));
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to create home schedule.", e);
            return ResponseDto.fail("Failed to create home schedule.");
        }
    }

    @Transactional
    public ResponseDto<AdminHomeScheduleResponse> updateSchedule(Long scheduleId, AdminHomeScheduleUpdateRequest request) {
        try {
            if (scheduleId == null) {
                throw new NoSuchElementException("Home schedule not found.");
            }

            NormalizedSchedule normalized = normalizeAndValidate(
                    request == null ? null : request.getScheduleGroup(),
                    request == null ? null : request.getTitle(),
                    request == null ? null : request.getDescription(),
                    request == null ? null : request.getScheduledAt(),
                    request == null ? null : request.getTargetUrl(),
                    request == null ? null : request.getLinkType(),
                    request == null ? null : request.getDisplayPriority()
            );

            HomeScheduleEntity schedule = homeScheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new NoSuchElementException("Home schedule not found."));
            schedule.update(
                    normalized.scheduleGroup(),
                    normalized.title(),
                    normalized.description(),
                    normalized.scheduledAt(),
                    normalized.targetUrl(),
                    normalized.linkType(),
                    normalized.displayPriority()
            );
            if (request != null && request.getMatches() != null) {
                schedule.replaceMatches(normalizeMatches(request.getMatches()));
            }

            return ResponseDto.success(toAdminResponse(schedule, LocalDateTime.now(), loadScheduleRelations(List.of(schedule), true)));
        } catch (NoSuchElementException e) {
            return notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to update home schedule. scheduleId={}", scheduleId, e);
            return ResponseDto.fail("Failed to update home schedule.");
        }
    }

    @Transactional
    public ResponseDto<Void> deleteSchedule(Long scheduleId) {
        try {
            if (scheduleId == null) {
                throw new NoSuchElementException("Home schedule not found.");
            }

            HomeScheduleEntity schedule = homeScheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new NoSuchElementException("Home schedule not found."));
            homeScheduleRepository.delete(schedule);
            return ResponseDto.success(null);
        } catch (NoSuchElementException e) {
            return notFound(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to delete home schedule. scheduleId={}", scheduleId, e);
            return ResponseDto.fail("Failed to delete home schedule.");
        }
    }

    @Transactional
    public ResponseDto<AdminHomeScheduleDeleteResponse> deleteSchedules(AdminHomeScheduleDeleteRequest request) {
        try {
            List<Long> scheduleIds = normalizeScheduleIds(request == null ? null : request.getScheduleIds());
            List<HomeScheduleEntity> schedules = homeScheduleRepository.findAllById(scheduleIds);
            if (schedules.size() != scheduleIds.size()) {
                throw new NoSuchElementException("Home schedule not found.");
            }

            Map<Long, HomeScheduleEntity> scheduleById = schedules.stream()
                    .collect(Collectors.toMap(HomeScheduleEntity::getId, Function.identity()));
            for (Long scheduleId : scheduleIds) {
                homeScheduleRepository.delete(scheduleById.get(scheduleId));
            }

            return ResponseDto.success(AdminHomeScheduleDeleteResponse.builder()
                    .deletedCount(scheduleIds.size())
                    .build());
        } catch (NoSuchElementException e) {
            return notFound(e.getMessage());
        } catch (IllegalArgumentException e) {
            return validationFailed(e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.warn("Failed to delete home schedules.", e);
            return ResponseDto.fail("Failed to delete home schedules.");
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<AdminHomeScheduleMapSearchResponse>> searchMaps(String keyword, Integer limit) {
        try {
            int normalizedLimit = normalizeLimit(limit, DEFAULT_MAP_SEARCH_LIMIT, MAX_MAP_SEARCH_LIMIT);
            String normalizedKeyword = normalizeBlankToEmpty(keyword).toLowerCase(Locale.ROOT);
            List<AdminHomeScheduleMapSearchResponse> maps = mapRepository
                    .searchByMapNameForAdmin(normalizedKeyword, PageRequest.of(0, normalizedLimit))
                    .stream()
                    .map(map -> AdminHomeScheduleMapSearchResponse.builder()
                            .id(map.getId())
                            .mapName(map.getMapName())
                            .image(map.getImage())
                            .build())
                    .toList();
            return ResponseDto.success(maps);
        } catch (Exception e) {
            log.warn("Failed to search home schedule maps. keyword={}", keyword, e);
            return ResponseDto.fail("Failed to search maps.");
        }
    }

    @Transactional(readOnly = true)
    public ResponseDto<List<AdminHomeScheduleProleagueTeamSearchResponse>> searchProleagueTeams(String keyword, Integer limit) {
        try {
            int normalizedLimit = normalizeLimit(
                    limit,
                    DEFAULT_PROLEAGUE_TEAM_SEARCH_LIMIT,
                    MAX_PROLEAGUE_TEAM_SEARCH_LIMIT
            );
            return ResponseDto.success(proleagueTeamQueryRepository.searchLiveProleagueTeams(keyword, normalizedLimit));
        } catch (Exception e) {
            log.warn("Failed to search live proleague teams. keyword={}", keyword, e);
            return ResponseDto.fail("Failed to search proleague teams.");
        }
    }

    private HomeScheduleResponse toPublicResponse(HomeScheduleEntity schedule, ScheduleRelations relations) {
        return HomeScheduleResponse.builder()
                .id(schedule.getId())
                .scheduleGroup(schedule.getScheduleGroup())
                .timeLabel(schedule.getScheduledAt() == null ? null : schedule.getScheduledAt().format(TIME_LABEL_FORMATTER))
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .scheduledAt(schedule.getScheduledAt())
                .targetUrl(schedule.getTargetUrl())
                .linkType(schedule.getLinkType())
                .navigationUrl(resolveNavigationUrl(schedule))
                .matches(toMatchResponses(schedule, relations))
                .build();
    }

    private AdminHomeScheduleResponse toAdminResponse(HomeScheduleEntity schedule, LocalDateTime now, ScheduleRelations relations) {
        return AdminHomeScheduleResponse.builder()
                .id(schedule.getId())
                .scheduleGroup(schedule.getScheduleGroup())
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .scheduledAt(schedule.getScheduledAt())
                .targetUrl(schedule.getTargetUrl())
                .linkType(schedule.getLinkType())
                .displayPriority(schedule.getDisplayPriority())
                .status(isUpcoming(schedule, now) ? STATUS_UPCOMING : STATUS_EXPIRED)
                .regDate(schedule.getRegDate())
                .updateDate(schedule.getUpdateDate())
                .matches(toMatchResponses(schedule, relations))
                .build();
    }

    private List<HomeScheduleMatchResponse> toMatchResponses(HomeScheduleEntity schedule, ScheduleRelations relations) {
        return matchesFor(schedule, relations).stream()
                .map(match -> {
                    List<HomeScheduleMatchPlayerResponse> players = playersFor(match, relations).stream()
                            .map(player -> toPlayerResponse(player, relations))
                            .toList();

                    return HomeScheduleMatchResponse.builder()
                            .id(match.getId())
                            .displayOrder(match.getDisplayOrder())
                            .setLabel(match.getSetLabel())
                            .matchFormat(match.getMatchFormat())
                            .teamAName(match.getTeamAName())
                            .teamBName(match.getTeamBName())
                            .mapId(match.getMapId())
                            .mapName(resolveMapName(match, relations))
                            .note(match.getNote())
                            .sideAPlayers(players.stream()
                                    .filter(player -> HomeScheduleMatchPlayerEntity.SIDE_A.equals(player.getSide()))
                                    .toList())
                            .sideBPlayers(players.stream()
                                    .filter(player -> HomeScheduleMatchPlayerEntity.SIDE_B.equals(player.getSide()))
                                    .toList())
                            .build();
                })
                .toList();
    }

    private HomeScheduleMatchPlayerResponse toPlayerResponse(
            HomeScheduleMatchPlayerEntity player,
            ScheduleRelations relations
    ) {
        UserEntity user = player.getUserId() == null ? null : relations.usersById().get(player.getUserId());
        return HomeScheduleMatchPlayerResponse.builder()
                .id(player.getId())
                .side(player.getSide())
                .slotOrder(player.getSlotOrder())
                .userId(player.getUserId())
                .playerName(resolvePlayerName(player, user))
                .playerRank(resolvePlayerRank(player, user))
                .playerRace(resolvePlayerRace(player, user))
                .note(player.getNote())
                .build();
    }

    private String resolvePlayerName(HomeScheduleMatchPlayerEntity player, UserEntity user) {
        if (user != null && user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId();
        }
        return player.getPlayerName();
    }

    private String resolvePlayerRank(HomeScheduleMatchPlayerEntity player, UserEntity user) {
        if (player.getPlayerRank() != null && !player.getPlayerRank().isBlank()) {
            return player.getPlayerRank();
        }
        return user == null ? null : user.getTier();
    }

    private String resolvePlayerRace(HomeScheduleMatchPlayerEntity player, UserEntity user) {
        if (player.getPlayerRace() != null && !player.getPlayerRace().isBlank()) {
            return player.getPlayerRace();
        }
        return user == null ? null : user.getRace();
    }

    private String resolveMapName(HomeScheduleMatchEntity match, ScheduleRelations relations) {
        if (match.getMapId() == null) {
            return null;
        }
        MapEntity map = relations.mapsById().get(match.getMapId());
        return map == null ? null : map.getMapName();
    }

    private ScheduleRelations loadScheduleRelations(List<HomeScheduleEntity> schedules, boolean includeEntityRelations) {
        if (schedules == null || schedules.isEmpty()) {
            return ScheduleRelations.empty();
        }

        List<Long> scheduleIds = schedules.stream()
                .map(HomeScheduleEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<HomeScheduleMatchEntity> repositoryMatches = scheduleIds.isEmpty()
                ? List.of()
                : homeScheduleMatchRepository.findByScheduleIdInOrderByScheduleIdAscDisplayOrderAscIdAsc(scheduleIds);
        if (repositoryMatches == null) {
            repositoryMatches = List.of();
        }
        List<HomeScheduleMatchEntity> allMatches = new ArrayList<>(repositoryMatches);

        Map<Long, List<HomeScheduleMatchEntity>> matchesByScheduleId = new HashMap<>();
        for (HomeScheduleMatchEntity match : repositoryMatches) {
            Long scheduleId = resolveScheduleId(match);
            if (scheduleId != null) {
                matchesByScheduleId.computeIfAbsent(scheduleId, ignored -> new ArrayList<>()).add(match);
            }
        }
        Map<HomeScheduleEntity, List<HomeScheduleMatchEntity>> matchesByScheduleInstance = new IdentityHashMap<>();
        if (includeEntityRelations) {
            for (HomeScheduleEntity schedule : schedules) {
                List<HomeScheduleMatchEntity> entityMatches = schedule.getMatches() == null
                        ? List.of()
                        : schedule.getMatches();
                matchesByScheduleInstance.put(schedule, entityMatches);
                allMatches.addAll(entityMatches);
            }
        }

        List<Long> matchIds = allMatches.stream()
                .map(HomeScheduleMatchEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<HomeScheduleMatchPlayerEntity> repositoryPlayers = matchIds.isEmpty()
                ? List.of()
                : homeScheduleMatchPlayerRepository.findByMatchIdInOrderByMatchIdAscSideAscSlotOrderAscIdAsc(matchIds);
        if (repositoryPlayers == null) {
            repositoryPlayers = List.of();
        }
        List<HomeScheduleMatchPlayerEntity> allPlayers = new ArrayList<>(repositoryPlayers);

        Map<Long, List<HomeScheduleMatchPlayerEntity>> playersByMatchId = new HashMap<>();
        for (HomeScheduleMatchPlayerEntity player : repositoryPlayers) {
            Long matchId = resolveMatchId(player);
            if (matchId != null) {
                playersByMatchId.computeIfAbsent(matchId, ignored -> new ArrayList<>()).add(player);
            }
        }
        Map<HomeScheduleMatchEntity, List<HomeScheduleMatchPlayerEntity>> playersByMatchInstance = new IdentityHashMap<>();
        if (includeEntityRelations) {
            for (HomeScheduleMatchEntity match : allMatches) {
                List<HomeScheduleMatchPlayerEntity> entityPlayers = match.getPlayers() == null
                        ? List.of()
                        : match.getPlayers();
                playersByMatchInstance.put(match, entityPlayers);
                allPlayers.addAll(entityPlayers);
            }
        }

        Map<Long, MapEntity> mapsById = loadMaps(allMatches.stream()
                .map(HomeScheduleMatchEntity::getMapId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, UserEntity> usersById = loadUsers(allPlayers.stream()
                .map(HomeScheduleMatchPlayerEntity::getUserId)
                .filter(Objects::nonNull)
                .toList());

        return new ScheduleRelations(
                matchesByScheduleId,
                matchesByScheduleInstance,
                playersByMatchId,
                playersByMatchInstance,
                mapsById,
                usersById
        );
    }

    private Long resolveScheduleId(HomeScheduleMatchEntity match) {
        if (match == null) {
            return null;
        }
        if (match.getScheduleId() != null) {
            return match.getScheduleId();
        }
        return match.getSchedule() == null ? null : match.getSchedule().getId();
    }

    private Long resolveMatchId(HomeScheduleMatchPlayerEntity player) {
        if (player == null) {
            return null;
        }
        if (player.getMatchId() != null) {
            return player.getMatchId();
        }
        return player.getMatch() == null ? null : player.getMatch().getId();
    }

    private List<HomeScheduleMatchEntity> matchesFor(HomeScheduleEntity schedule, ScheduleRelations relations) {
        if (relations.matchesByScheduleInstance().containsKey(schedule)) {
            return relations.matchesByScheduleInstance().get(schedule);
        }
        if (schedule.getId() != null && relations.matchesByScheduleId().containsKey(schedule.getId())) {
            return relations.matchesByScheduleId().get(schedule.getId());
        }
        return List.of();
    }

    private List<HomeScheduleMatchPlayerEntity> playersFor(HomeScheduleMatchEntity match, ScheduleRelations relations) {
        List<HomeScheduleMatchPlayerEntity> players;
        if (relations.playersByMatchInstance().containsKey(match)) {
            players = relations.playersByMatchInstance().get(match);
        } else if (match.getId() != null && relations.playersByMatchId().containsKey(match.getId())) {
            players = relations.playersByMatchId().get(match.getId());
        } else {
            players = List.of();
        }
        return players.stream()
                .sorted(Comparator
                        .comparing(HomeScheduleMatchPlayerEntity::getSide, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(HomeScheduleMatchPlayerEntity::getSlotOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(HomeScheduleMatchPlayerEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Map<Long, MapEntity> loadMaps(List<Long> mapIds) {
        List<Long> distinctIds = mapIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return mapRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(MapEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, UserEntity> loadUsers(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity(), (left, right) -> left));
    }

    private Specification<HomeScheduleEntity> adminSearchSpec(
            String keyword,
            LocalDate fromDate,
            LocalDate toDate,
            String scheduleGroup
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description").as(String.class)), pattern));
            }

            if (scheduleGroup != null) {
                predicates.add(criteriaBuilder.equal(root.get("scheduleGroup"), scheduleGroup));
            }
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("scheduledAt"), fromDate.atStartOfDay()));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("scheduledAt"), toDate.plusDays(1).atStartOfDay()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private NormalizedSchedule normalizeAndValidate(
            String scheduleGroup,
            String title,
            String description,
            LocalDateTime scheduledAt,
            String targetUrl,
            String linkType,
            Integer displayPriority
    ) {
        String normalizedScheduleGroup = normalizeBlankToNull(scheduleGroup);
        if (normalizedScheduleGroup == null) {
            throw new IllegalArgumentException("scheduleGroup is required.");
        }
        if (normalizedScheduleGroup.length() > 50) {
            throw new IllegalArgumentException("scheduleGroup must be 50 characters or less.");
        }

        String normalizedTitle = normalizeBlankToNull(title);
        if (normalizedTitle == null) {
            throw new IllegalArgumentException("title is required.");
        }
        if (normalizedTitle.length() > 200) {
            throw new IllegalArgumentException("title must be 200 characters or less.");
        }

        String normalizedDescription = normalizeBlankToNull(description);
        if (normalizedDescription != null && normalizedDescription.length() > 1000) {
            throw new IllegalArgumentException("description must be 1000 characters or less.");
        }

        if (scheduledAt == null) {
            throw new IllegalArgumentException("scheduledAt is required.");
        }

        String normalizedTargetUrl = normalizeBlankToNull(targetUrl);
        if (normalizedTargetUrl != null && normalizedTargetUrl.length() > 500) {
            throw new IllegalArgumentException("targetUrl must be 500 characters or less.");
        }

        String normalizedLinkType = normalizeLinkType(linkType);

        return new NormalizedSchedule(
                normalizedScheduleGroup,
                normalizedTitle,
                normalizedDescription,
                scheduledAt,
                normalizedTargetUrl,
                normalizedLinkType,
                displayPriority == null ? 0 : displayPriority
        );
    }

    private List<HomeScheduleMatchEntity> normalizeMatches(List<AdminHomeScheduleMatchRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        validateReferencedMaps(requests);
        validateReferencedUsers(requests);

        List<HomeScheduleMatchEntity> matches = new ArrayList<>();
        for (AdminHomeScheduleMatchRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("matches cannot contain null.");
            }

            Integer displayOrder = request.getDisplayOrder();
            if (displayOrder == null || displayOrder <= 0) {
                throw new IllegalArgumentException("match displayOrder must be greater than 0.");
            }
            String setLabel = normalizeLength(normalizeBlankToNull(request.getSetLabel()), "setLabel", 50, true);
            String matchFormat = normalizeMatchFormat(request.getMatchFormat());

            HomeScheduleMatchEntity match = HomeScheduleMatchEntity.builder()
                    .displayOrder(displayOrder)
                    .setLabel(setLabel)
                    .matchFormat(matchFormat)
                    .teamAName(normalizeLength(normalizeBlankToNull(request.getTeamAName()), "teamAName", 100, false))
                    .teamBName(normalizeLength(normalizeBlankToNull(request.getTeamBName()), "teamBName", 100, false))
                    .mapId(request.getMapId())
                    .note(normalizeLength(normalizeBlankToNull(request.getNote()), "match note", 300, false))
                    .build();
            match.replacePlayers(normalizePlayers(request.getPlayers()));
            matches.add(match);
        }
        return matches;
    }

    private List<HomeScheduleMatchPlayerEntity> normalizePlayers(List<AdminHomeScheduleMatchPlayerRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> occupiedSlots = new HashSet<>();
        List<HomeScheduleMatchPlayerEntity> players = new ArrayList<>();
        for (AdminHomeScheduleMatchPlayerRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("players cannot contain null.");
            }

            String side = normalizeSide(request.getSide());
            Integer slotOrder = request.getSlotOrder();
            if (slotOrder == null || slotOrder <= 0) {
                throw new IllegalArgumentException("player slotOrder must be greater than 0.");
            }
            String slotKey = side + ":" + slotOrder;
            if (!occupiedSlots.add(slotKey)) {
                throw new IllegalArgumentException("duplicate player slot: " + slotKey);
            }

            String playerName = normalizeLength(normalizeBlankToNull(request.getPlayerName()), "playerName", 100, false);
            if (request.getUserId() == null && playerName == null) {
                throw new IllegalArgumentException("playerName is required when userId is null.");
            }

            players.add(HomeScheduleMatchPlayerEntity.builder()
                    .side(side)
                    .slotOrder(slotOrder)
                    .userId(request.getUserId())
                    .playerName(playerName)
                    .playerRank(normalizeLength(normalizeBlankToNull(request.getPlayerRank()), "playerRank", 20, false))
                    .playerRace(normalizeRace(request.getPlayerRace()))
                    .note(normalizeLength(normalizeBlankToNull(request.getNote()), "player note", 300, false))
                    .build());
        }
        return players;
    }

    private void validateReferencedMaps(List<AdminHomeScheduleMatchRequest> requests) {
        List<Long> mapIds = requests.stream()
                .filter(Objects::nonNull)
                .map(AdminHomeScheduleMatchRequest::getMapId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (mapIds.isEmpty()) {
            return;
        }

        Set<Long> existingIds = mapRepository.findAllById(mapIds).stream()
                .map(MapEntity::getId)
                .collect(Collectors.toSet());
        if (existingIds.size() != mapIds.size()) {
            throw new IllegalArgumentException("mapId not found.");
        }
    }

    private void validateReferencedUsers(List<AdminHomeScheduleMatchRequest> requests) {
        List<Long> userIds = requests.stream()
                .filter(Objects::nonNull)
                .flatMap(match -> match.getPlayers() == null ? List.<AdminHomeScheduleMatchPlayerRequest>of().stream() : match.getPlayers().stream())
                .filter(Objects::nonNull)
                .map(AdminHomeScheduleMatchPlayerRequest::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }

        Set<Long> existingIds = userRepository.findAllById(userIds).stream()
                .map(UserEntity::getId)
                .collect(Collectors.toSet());
        if (existingIds.size() != userIds.size()) {
            throw new IllegalArgumentException("userId not found.");
        }
    }

    private String normalizeMatchFormat(String matchFormat) {
        String normalized = normalizeBlankToNull(matchFormat);
        if (normalized == null) {
            return HomeScheduleMatchEntity.FORMAT_1V1;
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!MATCH_FORMATS.contains(normalized)) {
            throw new IllegalArgumentException("matchFormat must be 1V1, 2V2, 3V3, ACE, or CUSTOM.");
        }
        return normalized;
    }

    private String normalizeSide(String side) {
        String normalized = normalizeBlankToNull(side);
        if (normalized == null) {
            throw new IllegalArgumentException("side is required.");
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!HomeScheduleMatchPlayerEntity.SIDE_A.equals(normalized)
                && !HomeScheduleMatchPlayerEntity.SIDE_B.equals(normalized)) {
            throw new IllegalArgumentException("side must be A or B.");
        }
        return normalized;
    }

    private String normalizeRace(String race) {
        String normalized = normalizeBlankToNull(race);
        if (normalized == null) {
            return null;
        }

        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!PLAYER_RACES.contains(normalized)) {
            throw new IllegalArgumentException("playerRace must be ZERG, TERRAN, PROTOSS, or RANDOM.");
        }
        return normalized;
    }

    private String normalizeLength(String value, String field, int maxLength, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(field + " is required.");
            }
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be " + maxLength + " characters or less.");
        }
        return value;
    }

    private List<Long> normalizeScheduleIds(List<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new IllegalArgumentException("scheduleIds is required.");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long scheduleId : scheduleIds) {
            if (scheduleId == null) {
                throw new IllegalArgumentException("scheduleIds cannot contain null.");
            }
            uniqueIds.add(scheduleId);
        }

        if (uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("scheduleIds is required.");
        }

        return uniqueIds.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String normalizeLinkType(String linkType) {
        String normalizedLinkType = normalizeBlankToNull(linkType);
        if (normalizedLinkType == null) {
            return HomeScheduleEntity.LINK_TYPE_DIRECT;
        }

        normalizedLinkType = normalizedLinkType.toUpperCase(Locale.ROOT);
        if (!HomeScheduleEntity.LINK_TYPE_DIRECT.equals(normalizedLinkType)
                && !HomeScheduleEntity.LINK_TYPE_REDIRECT.equals(normalizedLinkType)) {
            throw new IllegalArgumentException("linkType must be DIRECT or REDIRECT.");
        }
        return normalizedLinkType;
    }

    private String resolveNavigationUrl(HomeScheduleEntity schedule) {
        if (HomeScheduleEntity.LINK_TYPE_REDIRECT.equals(schedule.getLinkType())) {
            return "/home/schedules/" + schedule.getId() + "/redirect";
        }
        return schedule.getTargetUrl();
    }

    private boolean isUpcoming(HomeScheduleEntity schedule, LocalDateTime now) {
        return schedule.getScheduledAt() != null && !schedule.getScheduledAt().isBefore(now);
    }

    private int normalizePage(Integer page) {
        if (page == null || page < DEFAULT_ADMIN_PAGE) {
            return DEFAULT_ADMIN_PAGE;
        }
        return page;
    }

    private int normalizeLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, maxLimit);
    }

    private String normalizeBlankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeBlankToEmpty(String value) {
        String normalized = normalizeBlankToNull(value);
        return normalized == null ? "" : normalized;
    }

    private <T> ResponseDto<T> validationFailed(String message) {
        return ResponseDto.fail(HttpServletResponse.SC_BAD_REQUEST, message, ApiErrorCode.VALIDATION_FAILED);
    }

    private <T> ResponseDto<T> notFound(String message) {
        return ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, message, ApiErrorCode.RESOURCE_NOT_FOUND);
    }

    private void markRollbackOnly() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }

    private record NormalizedSchedule(
            String scheduleGroup,
            String title,
            String description,
            LocalDateTime scheduledAt,
            String targetUrl,
            String linkType,
            Integer displayPriority
    ) {
    }

    private record ScheduleRelations(
            Map<Long, List<HomeScheduleMatchEntity>> matchesByScheduleId,
            Map<HomeScheduleEntity, List<HomeScheduleMatchEntity>> matchesByScheduleInstance,
            Map<Long, List<HomeScheduleMatchPlayerEntity>> playersByMatchId,
            Map<HomeScheduleMatchEntity, List<HomeScheduleMatchPlayerEntity>> playersByMatchInstance,
            Map<Long, MapEntity> mapsById,
            Map<Long, UserEntity> usersById
    ) {
        static ScheduleRelations empty() {
            return new ScheduleRelations(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
