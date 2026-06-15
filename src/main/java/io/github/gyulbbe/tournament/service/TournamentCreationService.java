package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.TournamentCreateGroupRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateMatchDefaultRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateMapDefaultRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateSlotRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSetEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentRouteEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentClanShareSendLogRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentRouteRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentCreationService {

    private static final int DEFAULT_BEST_OF = 3;
    private static final int MAX_DUAL_GROUP_SIZE = 4;
    private static final String ACTIVE_USER_STATUS = "ACTIVE";
    private static final String BYE_LABEL = "BYE";
    private static final String MAIN_GROUP_CODE = "MAIN";
    private static final String MAIN_GROUP_NAME = "Main Bracket";
    private static final String RACE_TERRAN = "TERRAN";
    private static final String RACE_ZERG = "ZERG";
    private static final String RACE_PROTOSS = "PROTOSS";
    private static final String MATCHES_GROUP_CODE = "MATCHES";
    private static final String RACE_SURVIVAL_EMPTY_SLOT_LABEL = "선수 지정";
    private static final String MAP_DEFAULT_TARGET_ROUND = "ROUND";
    private static final String MAP_DEFAULT_TARGET_MATCH_ROLE = "MATCH_ROLE";
    private static final String MATCH_DEFAULT_TARGET_ROUND = "ROUND";
    private static final String MATCH_DEFAULT_TARGET_MATCH_ROLE = "MATCH_ROLE";
    private static final List<String> RACE_SURVIVAL_GROUP_CODES = List.of(RACE_TERRAN, RACE_ZERG, RACE_PROTOSS);
    private static final Set<String> DUAL_MAP_DEFAULT_ROLES = Set.of(
            TournamentMatchEntity.ROLE_OPENING,
            TournamentMatchEntity.ROLE_WINNERS,
            TournamentMatchEntity.ROLE_LOSERS,
            TournamentMatchEntity.ROLE_DECIDER
    );

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentGroupRepository groupRepository;
    private final TournamentGroupEntryRepository groupEntryRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;
    private final TournamentMatchScoreSubmissionSetRepository scoreSubmissionSetRepository;
    private final TournamentClanShareSendLogRepository clanShareSendLogRepository;
    private final TournamentMatchSetRepository matchSetRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentRouteRepository routeRepository;
    private final TournamentResultSlotRepository resultSlotRepository;
    private final UserRepository userRepository;
    private final TournamentBracketProgressionService bracketProgressionService;
    private final TournamentService tournamentService;
    private final MapRepository mapRepository;

    @Transactional
    public TournamentDetailResponseDto createTournament(TournamentCreateRequestDto request, Long ownerUserId) {
        NormalizedTournament normalized = normalize(request, ownerUserId);

        TournamentEntity tournament = tournamentRepository.save(TournamentEntity.builder()
                .title(normalized.title())
                .ownerUserId(ownerUserId)
                .status(TournamentEntity.STATUS_LIVE)
                .build());

        createTournamentGraph(tournament.getId(), normalized);
        return tournamentService.buildDetail(tournament);
    }

    @Transactional
    public TournamentDetailResponseDto replaceTournament(Long tournamentId, TournamentCreateRequestDto request, Long ownerUserId) {
        NormalizedTournament normalized = normalize(request, ownerUserId);
        TournamentEntity tournament = tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new NoSuchElementException("Tournament not found. tournamentId=" + tournamentId));

        tournament.updateTitle(normalized.title());
        tournamentRepository.saveAndFlush(tournament);
        deleteTournamentGraph(tournament.getId());
        createTournamentGraph(tournament.getId(), normalized);
        return tournamentService.buildDetail(tournament);
    }

    private void createTournamentGraph(Long tournamentId, NormalizedTournament normalized) {
        Map<SlotKey, TournamentParticipantEntity> participantsBySlot = createParticipants(tournamentId, normalized);
        TournamentStageEntity stage = stageRepository.save(TournamentStageEntity.builder()
                .tournamentId(tournamentId)
                .stageNo(1)
                .stageName(defaultStageName(normalized.bracketType()))
                .stageType(normalized.bracketType())
                .status(TournamentStageEntity.STATUS_READY)
                .displayOrder(1)
                .build());

        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(normalized.bracketType())) {
            createSingleElimination(stage, normalized, participantsBySlot);
        } else if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(normalized.bracketType())) {
            createDualGroups(stage, normalized, participantsBySlot);
        } else if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(normalized.bracketType())) {
            createUltimateBattle(stage, normalized, participantsBySlot);
        } else if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(normalized.bracketType())) {
            createRaceSurvival(stage, normalized, participantsBySlot);
        }

        bracketProgressionService.applyByeWinsForStage(stage.getId());
    }

    private void deleteTournamentGraph(Long tournamentId) {
        List<TournamentStageEntity> stages = stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournamentId);
        List<Long> stageIds = stages.stream()
                .map(TournamentStageEntity::getId)
                .toList();
        List<TournamentGroupEntity> groups = stageIds.isEmpty()
                ? List.of()
                : groupRepository.findAllByStageIdInOrderByDisplayOrderAsc(stageIds);
        List<Long> groupIds = groups.stream()
                .map(TournamentGroupEntity::getId)
                .toList();
        Map<Long, TournamentMatchEntity> matchesById = new HashMap<>();
        if (!groupIds.isEmpty()) {
            matchRepository.findAllByGroupIdInOrderByDisplayOrderAsc(groupIds)
                    .forEach(match -> matchesById.putIfAbsent(match.getId(), match));
        }
        if (!stageIds.isEmpty()) {
            matchRepository.findAllByStageIdInOrderByDisplayOrderAsc(stageIds)
                    .forEach(match -> matchesById.putIfAbsent(match.getId(), match));
        }
        List<Long> matchIds = matchesById.keySet().stream()
                .sorted()
                .toList();

        clanShareSendLogRepository.deleteByTournamentId(tournamentId);
        scoreSubmissionSetRepository.deleteByTournamentId(tournamentId);
        scoreSubmissionRepository.deleteByTournamentId(tournamentId);
        if (!matchIds.isEmpty()) {
            routeRepository.deleteByFromMatchIdIn(matchIds);
            routeRepository.deleteByToMatchIdIn(matchIds);
            matchSetRepository.deleteByMatchIdIn(matchIds);
            matchSlotRepository.deleteByMatchIdIn(matchIds);
        }
        if (!groupIds.isEmpty()) {
            resultSlotRepository.deleteByGroupIdIn(groupIds);
            groupEntryRepository.deleteByGroupIdIn(groupIds);
            matchRepository.deleteByGroupIdIn(groupIds);
        }
        if (!stageIds.isEmpty()) {
            resultSlotRepository.deleteByStageIdIn(stageIds);
            matchRepository.deleteByStageIdIn(stageIds);
            groupRepository.deleteByStageIdIn(stageIds);
        }
        stageRepository.deleteByTournamentId(tournamentId);
        participantRepository.deleteByTournamentId(tournamentId);
    }

    private NormalizedTournament normalize(TournamentCreateRequestDto request, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("ownerUserId is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String title = requireText(request.getTitle(), "title is required.");
        String bracketType = normalizeBracketType(request.getBracketType());
        int bestOf = normalizeBestOf(request.getBestOf());
        List<TournamentCreateGroupRequestDto> requestedGroups = request.getGroups();
        if (requestedGroups == null || requestedGroups.isEmpty()) {
            throw new IllegalArgumentException("groups must not be empty.");
        }

        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType) && requestedGroups.size() != 1) {
            throw new IllegalArgumentException("SINGLE_ELIMINATION allows exactly one group.");
        }
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType) && requestedGroups.size() != 1) {
            throw new IllegalArgumentException("ULTIMATE_BATTLE allows exactly one group.");
        }
        if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(bracketType) && requestedGroups.size() != RACE_SURVIVAL_GROUP_CODES.size()) {
            throw new IllegalArgumentException("RACE_SURVIVAL requires TERRAN, ZERG, and PROTOSS groups.");
        }

        List<NormalizedGroup> groups = new ArrayList<>();
        Set<Long> userIds = new LinkedHashSet<>();
        Set<String> groupCodes = new HashSet<>();
        int participantCount = 0;
        for (int groupIndex = 0; groupIndex < requestedGroups.size(); groupIndex++) {
            NormalizedGroup group = normalizeGroup(
                    requestedGroups.get(groupIndex),
                    groupIndex,
                    bracketType,
                    userIds
            );
            if (!groupCodes.add(group.groupCode())) {
                throw new IllegalArgumentException("Duplicate groupCode is not allowed.");
            }
            participantCount += group.slots().size();
            groups.add(group);
        }

        if (participantCount < 2) {
            throw new IllegalArgumentException("At least two participants are required.");
        }
        validateSpecialBracket(bracketType, groups, participantCount);
        verifyUsers(userIds);
        NormalizedMapDefaults mapDefaults = normalizeMapDefaults(bracketType, request.getMapDefaults());
        NormalizedMatchDefaults matchDefaults = normalizeMatchDefaults(bracketType, bestOf, request.getMatchDefaults());

        return new NormalizedTournament(
                title,
                bracketType,
                bestOf,
                groups,
                mapDefaults,
                matchDefaults
        );
    }

    private NormalizedGroup normalizeGroup(
            TournamentCreateGroupRequestDto request,
            int groupIndex,
            String bracketType,
            Set<Long> userIds
    ) {
        List<TournamentCreateSlotRequestDto> requestedSlots = request == null ? null : request.getSlots();
        if (requestedSlots == null || requestedSlots.isEmpty()) {
            if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType)) {
                throw new IllegalArgumentException("DUAL_GROUP requires at least two participants per group.");
            }
            throw new IllegalArgumentException("Each group must have at least one participant slot.");
        }
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType) && requestedSlots.size() > MAX_DUAL_GROUP_SIZE) {
            throw new IllegalArgumentException("DUAL_GROUP allows up to four participants per group.");
        }

        String groupCode = normalizeGroupCode(request, groupIndex, bracketType);
        String groupName = normalizeGroupName(request, groupCode, bracketType);
        Set<Integer> slotNumbers = new HashSet<>();
        List<NormalizedSlot> slots = new ArrayList<>();
        for (TournamentCreateSlotRequestDto requestedSlot : requestedSlots) {
            NormalizedSlot slot = normalizeSlot(requestedSlot, groupIndex, bracketType);
            if (!slotNumbers.add(slot.slotNo())) {
                throw new IllegalArgumentException("Duplicate slotNo is not allowed in a group.");
            }
            if (slot.userId() != null && !userIds.add(slot.userId())) {
                throw new IllegalArgumentException("Duplicate userId is not allowed in a tournament.");
            }
            slots.add(slot);
        }

        slots.sort((left, right) -> Integer.compare(left.slotNo(), right.slotNo()));
        validateDualGroupSlots(bracketType, slots);
        return new NormalizedGroup(groupIndex, groupCode, groupName, slots);
    }

    private void validateDualGroupSlots(String bracketType, List<NormalizedSlot> slots) {
        if (!TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType)) {
            return;
        }

        if (slots.size() < 2) {
            throw new IllegalArgumentException("DUAL_GROUP requires at least two participants per group.");
        }

        if (slots.size() != 2) {
            return;
        }

        long openingOneCount = slots.stream()
                .filter(slot -> slot.slotNo() == 1 || slot.slotNo() == 2)
                .count();
        long openingTwoCount = slots.stream()
                .filter(slot -> slot.slotNo() == 3 || slot.slotNo() == 4)
                .count();

        if (openingOneCount != 1 || openingTwoCount != 1) {
            throw new IllegalArgumentException("DUAL_GROUP with two participants requires one participant in each opening match.");
        }
    }

    private void validateSpecialBracket(String bracketType, List<NormalizedGroup> groups, int participantCount) {
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            if (participantCount != 2 || groups.get(0).slots().size() != 2) {
                throw new IllegalArgumentException("ULTIMATE_BATTLE requires exactly two participants.");
            }
            return;
        }
        if (!TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(bracketType)) {
            return;
        }

        Set<String> groupCodes = groups.stream()
                .map(NormalizedGroup::groupCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!groupCodes.equals(new LinkedHashSet<>(RACE_SURVIVAL_GROUP_CODES))) {
            throw new IllegalArgumentException("RACE_SURVIVAL groups must be TERRAN, ZERG, and PROTOSS.");
        }
        for (NormalizedGroup group : groups) {
            if (group.slots().isEmpty()) {
                throw new IllegalArgumentException("Each RACE_SURVIVAL group requires at least one participant.");
            }
        }
    }

    private NormalizedMapDefaults normalizeMapDefaults(
            String bracketType,
            List<TournamentCreateMapDefaultRequestDto> requestedMapDefaults
    ) {
        if (requestedMapDefaults == null || requestedMapDefaults.isEmpty()) {
            return NormalizedMapDefaults.empty();
        }
        if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(bracketType)) {
            throw new IllegalArgumentException("RACE_SURVIVAL does not accept mapDefaults.");
        }

        Map<Integer, Long> roundMapIds = new HashMap<>();
        Map<String, Long> roleMapIds = new HashMap<>();
        Set<Long> mapIds = new LinkedHashSet<>();

        for (TournamentCreateMapDefaultRequestDto requestedMapDefault : requestedMapDefaults) {
            if (requestedMapDefault == null) {
                throw new IllegalArgumentException("mapDefaults cannot contain null.");
            }

            String target = requireText(requestedMapDefault.getTarget(), "mapDefaults.target is required.").toUpperCase();
            Long mapId = requestedMapDefault.getMapId();
            if (mapId == null || mapId <= 0) {
                throw new IllegalArgumentException("mapDefaults.mapId must be a positive number.");
            }
            mapIds.add(mapId);

            if (MAP_DEFAULT_TARGET_ROUND.equals(target)) {
                normalizeRoundMapDefault(bracketType, requestedMapDefault, mapId, roundMapIds);
            } else if (MAP_DEFAULT_TARGET_MATCH_ROLE.equals(target)) {
                normalizeRoleMapDefault(bracketType, requestedMapDefault, mapId, roleMapIds);
            } else {
                throw new IllegalArgumentException("mapDefaults.target must be ROUND or MATCH_ROLE.");
            }
        }

        validateMapIds(mapIds);
        return new NormalizedMapDefaults(Map.copyOf(roundMapIds), Map.copyOf(roleMapIds));
    }

    private void normalizeRoundMapDefault(
            String bracketType,
            TournamentCreateMapDefaultRequestDto request,
            Long mapId,
            Map<Integer, Long> roundMapIds
    ) {
        if (!TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)) {
            throw new IllegalArgumentException("ROUND mapDefaults are only allowed for SINGLE_ELIMINATION.");
        }
        if (trimToNull(request.getMatchRole()) != null) {
            throw new IllegalArgumentException("ROUND mapDefaults cannot include matchRole.");
        }
        Integer roundNo = request.getRoundNo();
        if (roundNo == null || roundNo <= 0) {
            throw new IllegalArgumentException("ROUND mapDefaults require a positive roundNo.");
        }
        if (roundMapIds.put(roundNo, mapId) != null) {
            throw new IllegalArgumentException("Duplicate ROUND mapDefault is not allowed.");
        }
    }

    private void normalizeRoleMapDefault(
            String bracketType,
            TournamentCreateMapDefaultRequestDto request,
            Long mapId,
            Map<String, Long> roleMapIds
    ) {
        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)) {
            throw new IllegalArgumentException("MATCH_ROLE mapDefaults are not allowed for SINGLE_ELIMINATION.");
        }
        if (request.getRoundNo() != null) {
            throw new IllegalArgumentException("MATCH_ROLE mapDefaults cannot include roundNo.");
        }

        String matchRole = requireText(request.getMatchRole(), "MATCH_ROLE mapDefaults require matchRole.").toUpperCase();
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType)) {
            if (!DUAL_MAP_DEFAULT_ROLES.contains(matchRole)) {
                throw new IllegalArgumentException("DUAL_GROUP mapDefaults require OPENING, WINNERS, LOSERS, or DECIDER matchRole.");
            }
        } else if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            if (!TournamentMatchEntity.ROLE_FINAL.equals(matchRole)) {
                throw new IllegalArgumentException("ULTIMATE_BATTLE mapDefaults require FINAL matchRole.");
            }
        } else {
            throw new IllegalArgumentException("MATCH_ROLE mapDefaults are not allowed for this bracketType.");
        }

        if (roleMapIds.put(matchRole, mapId) != null) {
            throw new IllegalArgumentException("Duplicate MATCH_ROLE mapDefault is not allowed.");
        }
    }

    private void validateMapIds(Set<Long> mapIds) {
        if (mapIds.isEmpty()) {
            return;
        }
        Set<Long> existingMapIds = mapRepository.findAllById(mapIds).stream()
                .map(MapEntity::getId)
                .collect(Collectors.toSet());
        List<Long> missingMapIds = mapIds.stream()
                .filter(mapId -> !existingMapIds.contains(mapId))
                .toList();
        if (!missingMapIds.isEmpty()) {
            throw new IllegalArgumentException("Unknown mapId in mapDefaults.");
        }
    }

    private NormalizedMatchDefaults normalizeMatchDefaults(
            String bracketType,
            int defaultBestOf,
            List<TournamentCreateMatchDefaultRequestDto> requestedMatchDefaults
    ) {
        if (requestedMatchDefaults == null || requestedMatchDefaults.isEmpty()) {
            return NormalizedMatchDefaults.empty();
        }
        if (!TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)
                && !TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType)
                && !TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            throw new IllegalArgumentException("matchDefaults are only allowed for SINGLE_ELIMINATION, DUAL_GROUP, or ULTIMATE_BATTLE.");
        }

        Map<Integer, MatchDefaultConfig> roundDefaults = new HashMap<>();
        Map<String, MatchDefaultConfig> roleDefaults = new HashMap<>();
        Set<Long> mapIds = new LinkedHashSet<>();

        for (TournamentCreateMatchDefaultRequestDto requestedMatchDefault : requestedMatchDefaults) {
            if (requestedMatchDefault == null) {
                throw new IllegalArgumentException("matchDefaults cannot contain null.");
            }

            String target = requireText(requestedMatchDefault.getTarget(), "matchDefaults.target is required.").toUpperCase();
            MatchDefaultConfig config = normalizeMatchDefaultConfig(defaultBestOf, requestedMatchDefault, mapIds);

            if (MATCH_DEFAULT_TARGET_ROUND.equals(target)) {
                normalizeRoundMatchDefault(bracketType, requestedMatchDefault, config, roundDefaults);
            } else if (MATCH_DEFAULT_TARGET_MATCH_ROLE.equals(target)) {
                normalizeRoleMatchDefault(bracketType, requestedMatchDefault, config, roleDefaults);
            } else {
                throw new IllegalArgumentException("matchDefaults.target must be ROUND or MATCH_ROLE.");
            }
        }

        validateMapIds(mapIds);
        return new NormalizedMatchDefaults(Map.copyOf(roundDefaults), Map.copyOf(roleDefaults));
    }

    private MatchDefaultConfig normalizeMatchDefaultConfig(
            int defaultBestOf,
            TournamentCreateMatchDefaultRequestDto request,
            Set<Long> mapIds
    ) {
        Integer configuredBestOf = request.getBestOf();
        Integer normalizedBestOf = configuredBestOf == null ? null : normalizeBestOf(configuredBestOf);
        int effectiveBestOf = normalizedBestOf == null ? defaultBestOf : normalizedBestOf;
        List<Long> normalizedMapIds = normalizeMatchDefaultMapIds(request.getMapIds(), effectiveBestOf, mapIds);
        return new MatchDefaultConfig(normalizedBestOf, normalizedMapIds);
    }

    private List<Long> normalizeMatchDefaultMapIds(List<Long> requestedMapIds, int effectiveBestOf, Set<Long> mapIds) {
        if (requestedMapIds == null || requestedMapIds.isEmpty()) {
            return List.of();
        }
        if (requestedMapIds.size() > effectiveBestOf) {
            throw new IllegalArgumentException("matchDefaults.mapIds cannot contain more entries than bestOf.");
        }

        List<Long> normalizedMapIds = new ArrayList<>();
        for (Long mapId : requestedMapIds) {
            if (mapId != null && mapId <= 0) {
                throw new IllegalArgumentException("matchDefaults.mapIds must contain positive map IDs or null.");
            }
            if (mapId != null) {
                mapIds.add(mapId);
            }
            normalizedMapIds.add(mapId);
        }
        return Collections.unmodifiableList(normalizedMapIds);
    }

    private void normalizeRoundMatchDefault(
            String bracketType,
            TournamentCreateMatchDefaultRequestDto request,
            MatchDefaultConfig config,
            Map<Integer, MatchDefaultConfig> roundDefaults
    ) {
        if (!TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)) {
            throw new IllegalArgumentException("ROUND matchDefaults are only allowed for SINGLE_ELIMINATION.");
        }
        if (trimToNull(request.getMatchRole()) != null) {
            throw new IllegalArgumentException("ROUND matchDefaults cannot include matchRole.");
        }
        Integer roundNo = request.getRoundNo();
        if (roundNo == null || roundNo <= 0) {
            throw new IllegalArgumentException("ROUND matchDefaults require a positive roundNo.");
        }
        if (roundDefaults.put(roundNo, config) != null) {
            throw new IllegalArgumentException("Duplicate ROUND matchDefault is not allowed.");
        }
    }

    private void normalizeRoleMatchDefault(
            String bracketType,
            TournamentCreateMatchDefaultRequestDto request,
            MatchDefaultConfig config,
            Map<String, MatchDefaultConfig> roleDefaults
    ) {
        if (!TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType)
                && !TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            throw new IllegalArgumentException("MATCH_ROLE matchDefaults are only allowed for DUAL_GROUP or ULTIMATE_BATTLE.");
        }
        if (request.getRoundNo() != null) {
            throw new IllegalArgumentException("MATCH_ROLE matchDefaults cannot include roundNo.");
        }

        String matchRole = requireText(request.getMatchRole(), "MATCH_ROLE matchDefaults require matchRole.").toUpperCase();
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType) && !DUAL_MAP_DEFAULT_ROLES.contains(matchRole)) {
            throw new IllegalArgumentException("DUAL_GROUP matchDefaults require OPENING, WINNERS, LOSERS, or DECIDER matchRole.");
        }
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)
                && !TournamentMatchEntity.ROLE_FINAL.equals(matchRole)) {
            throw new IllegalArgumentException("ULTIMATE_BATTLE matchDefaults require FINAL matchRole.");
        }
        if (roleDefaults.put(matchRole, config) != null) {
            throw new IllegalArgumentException("Duplicate MATCH_ROLE matchDefault is not allowed.");
        }
    }

    private NormalizedSlot normalizeSlot(TournamentCreateSlotRequestDto request, int groupIndex, String bracketType) {
        if (request == null) {
            throw new IllegalArgumentException("slot must not be null.");
        }
        Integer slotNo = request.getSlotNo();
        if (slotNo == null || slotNo < 1) {
            throw new IllegalArgumentException("slotNo must be a positive number.");
        }
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(bracketType) && slotNo > MAX_DUAL_GROUP_SIZE) {
            throw new IllegalArgumentException("DUAL_GROUP slotNo must be between 1 and 4.");
        }
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType) && slotNo > 2) {
            throw new IllegalArgumentException("ULTIMATE_BATTLE slotNo must be between 1 and 2.");
        }
        Long userId = request.getUserId();
        String participantName = trimToNull(request.getParticipantName());
        if (userId == null) {
            if (participantName == null) {
                throw new IllegalArgumentException("participantName is required for external participants.");
            }
            if (BYE_LABEL.equalsIgnoreCase(participantName)) {
                throw new IllegalArgumentException("BYE must be represented as a slot, not a participant.");
            }
        } else {
            participantName = null;
        }

        return new NormalizedSlot(new SlotKey(groupIndex, slotNo), slotNo, userId, participantName);
    }

    private void verifyUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }

        Set<Long> activeUserIds = new HashSet<>();
        for (UserEntity user : userRepository.findAllById(userIds)) {
            if (ACTIVE_USER_STATUS.equals(user.getStatus())) {
                activeUserIds.add(user.getId());
            }
        }

        if (activeUserIds.size() != userIds.size()) {
            throw new IllegalArgumentException("All internal participants must be ACTIVE users.");
        }
    }

    private Map<SlotKey, TournamentParticipantEntity> createParticipants(
            Long tournamentId,
            NormalizedTournament normalized
    ) {
        Map<SlotKey, TournamentParticipantEntity> participantsBySlot = new HashMap<>();
        int seedNo = 1;
        for (NormalizedGroup group : normalized.groups()) {
            for (NormalizedSlot slot : group.slots()) {
                TournamentParticipantEntity participant = participantRepository.save(TournamentParticipantEntity.builder()
                        .tournamentId(tournamentId)
                        .userId(slot.userId())
                        .participantName(slot.participantName())
                        .seedNo(seedNo++)
                        .status(TournamentParticipantEntity.STATUS_READY)
                        .build());
                participantsBySlot.put(slot.key(), participant);
            }
        }
        return participantsBySlot;
    }

    private void createSingleElimination(
            TournamentStageEntity stage,
            NormalizedTournament normalized,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot
    ) {
        NormalizedGroup normalizedGroup = normalized.groups().get(0);
        TournamentGroupEntity group = groupRepository.save(TournamentGroupEntity.builder()
                .stageId(stage.getId())
                .groupCode(normalizedGroup.groupCode())
                .groupName(normalizedGroup.groupName())
                .displayOrder(1)
                .build());
        createGroupEntries(group, normalizedGroup, participantsBySlot);

        List<TournamentParticipantEntity> orderedParticipants = normalizedGroup.slots().stream()
                .map(slot -> participantsBySlot.get(slot.key()))
                .filter(Objects::nonNull)
                .toList();
        int bracketSize = nextPowerOfTwo(orderedParticipants.size());
        List<TournamentParticipantEntity> firstRoundSlots = distributeSingleEliminationByes(orderedParticipants, bracketSize);
        List<List<TournamentMatchEntity>> rounds = createSingleMatches(stage, group, normalized.bestOf(), bracketSize, normalized.mapDefaults(), normalized.matchDefaults());
        Map<Long, Boolean> byeOpeningMatches = createSingleSlots(rounds, firstRoundSlots);

        TournamentResultSlotEntity champion = resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .resultKey("CHAMPION")
                .resultType(TournamentResultSlotEntity.TYPE_CHAMPION)
                .rankNo(1)
                .label("Champion")
                .build());
        TournamentResultSlotEntity runnerUp = resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .resultKey("RUNNER_UP")
                .resultType(TournamentResultSlotEntity.TYPE_RUNNER_UP)
                .rankNo(2)
                .label("Runner-up")
                .build());

        createSingleRoutes(rounds, byeOpeningMatches, champion, runnerUp);
    }

    private List<List<TournamentMatchEntity>> createSingleMatches(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            int bestOf,
            int bracketSize,
            NormalizedMapDefaults mapDefaults,
            NormalizedMatchDefaults matchDefaults
    ) {
        List<List<TournamentMatchEntity>> rounds = new ArrayList<>();
        int matchesInRound = bracketSize / 2;
        int roundNo = 1;
        int displayOrder = 1;
        while (matchesInRound >= 1) {
            List<TournamentMatchEntity> roundMatches = new ArrayList<>();
            boolean finalRound = matchesInRound == 1;
            int roundBestOf = matchDefaults.bestOfForRound(roundNo, bestOf);
            List<Long> roundMapIds = matchDefaults.mapIdsForRound(roundNo);
            Long legacyMapId = mapDefaults.mapForRound(roundNo);
            for (int matchNo = 1; matchNo <= matchesInRound; matchNo++) {
                TournamentMatchEntity match = matchRepository.save(TournamentMatchEntity.builder()
                        .stageId(stage.getId())
                        .groupId(group.getId())
                        .matchKey(finalRound ? "FINAL" : "R" + roundNo + "M" + matchNo)
                        .matchRole(finalRound ? TournamentMatchEntity.ROLE_FINAL : TournamentMatchEntity.ROLE_ROUND)
                        .roundNo(roundNo)
                        .matchNo(matchNo)
                        .displayName(finalRound ? "Final" : "Round " + roundNo + " Match " + matchNo)
                        .bestOf(roundBestOf)
                        .status(roundNo == 1 ? TournamentMatchEntity.STATUS_READY : TournamentMatchEntity.STATUS_PENDING)
                        .mapId(firstMapId(roundMapIds, legacyMapId))
                        .layoutCol(roundNo)
                        .layoutRow(matchNo)
                        .displayOrder(displayOrder++)
                        .build());
                if (!roundMapIds.isEmpty()) {
                    saveMatchSetDefaults(match.getId(), roundMapIds);
                } else if (legacyMapId != null) {
                    saveMatchSetDefaults(match.getId(), List.of(legacyMapId));
                }
                roundMatches.add(match);
            }
            rounds.add(roundMatches);
            matchesInRound /= 2;
            roundNo++;
        }
        return rounds;
    }

    private Map<Long, Boolean> createSingleSlots(
            List<List<TournamentMatchEntity>> rounds,
            List<TournamentParticipantEntity> firstRoundSlots
    ) {
        Map<Long, Boolean> byeOpeningMatches = new HashMap<>();
        List<TournamentMatchEntity> firstRound = rounds.get(0);
        for (int matchIndex = 0; matchIndex < firstRound.size(); matchIndex++) {
            TournamentMatchEntity match = firstRound.get(matchIndex);
            TournamentParticipantEntity first = firstRoundSlots.get(matchIndex * 2);
            TournamentParticipantEntity second = firstRoundSlots.get(matchIndex * 2 + 1);
            saveParticipantOrByeSlot(match.getId(), 1, first);
            saveParticipantOrByeSlot(match.getId(), 2, second);
            byeOpeningMatches.put(match.getId(), first == null || second == null);
        }

        for (int roundIndex = 1; roundIndex < rounds.size(); roundIndex++) {
            List<TournamentMatchEntity> previousRound = rounds.get(roundIndex - 1);
            List<TournamentMatchEntity> currentRound = rounds.get(roundIndex);
            for (int matchIndex = 0; matchIndex < currentRound.size(); matchIndex++) {
                TournamentMatchEntity firstSource = previousRound.get(matchIndex * 2);
                TournamentMatchEntity secondSource = previousRound.get(matchIndex * 2 + 1);
                TournamentMatchEntity target = currentRound.get(matchIndex);
                saveSourceSlot(target.getId(), 1, firstSource.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, firstSource.getMatchKey() + " winner");
                saveSourceSlot(target.getId(), 2, secondSource.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, secondSource.getMatchKey() + " winner");
            }
        }

        return byeOpeningMatches;
    }

    private void createSingleRoutes(
            List<List<TournamentMatchEntity>> rounds,
            Map<Long, Boolean> byeOpeningMatches,
            TournamentResultSlotEntity champion,
            TournamentResultSlotEntity runnerUp
    ) {
        for (int roundIndex = 0; roundIndex < rounds.size() - 1; roundIndex++) {
            List<TournamentMatchEntity> sourceRound = rounds.get(roundIndex);
            List<TournamentMatchEntity> targetRound = rounds.get(roundIndex + 1);
            for (int matchIndex = 0; matchIndex < sourceRound.size(); matchIndex++) {
                TournamentMatchEntity source = sourceRound.get(matchIndex);
                TournamentMatchEntity target = targetRound.get(matchIndex / 2);
                int targetSlotNo = matchIndex % 2 + 1;
                saveMatchSlotRoute(source.getId(), TournamentRouteEntity.OUTCOME_WINNER, target.getId(), targetSlotNo);
                if (!Boolean.TRUE.equals(byeOpeningMatches.get(source.getId()))) {
                    saveEliminatedRoute(source.getId(), TournamentRouteEntity.OUTCOME_LOSER);
                }
            }
        }

        TournamentMatchEntity finalMatch = rounds.get(rounds.size() - 1).get(0);
        saveResultSlotRoute(finalMatch.getId(), TournamentRouteEntity.OUTCOME_WINNER, champion.getId());
        saveResultSlotRoute(finalMatch.getId(), TournamentRouteEntity.OUTCOME_LOSER, runnerUp.getId());
    }

    private void createDualGroups(
            TournamentStageEntity stage,
            NormalizedTournament normalized,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot
    ) {
        int displayOrder = 1;
        for (NormalizedGroup normalizedGroup : normalized.groups()) {
            TournamentGroupEntity group = groupRepository.save(TournamentGroupEntity.builder()
                    .stageId(stage.getId())
                    .groupCode(normalizedGroup.groupCode())
                    .groupName(normalizedGroup.groupName())
                    .displayOrder(normalizedGroup.groupIndex() + 1)
                    .build());
            createGroupEntries(group, normalizedGroup, participantsBySlot);
            displayOrder = createDualGroupBracket(stage, group, normalizedGroup, normalized.bestOf(), normalized.mapDefaults(), normalized.matchDefaults(), participantsBySlot, displayOrder);
        }
    }

    private int createDualGroupBracket(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            NormalizedGroup normalizedGroup,
            int bestOf,
            NormalizedMapDefaults mapDefaults,
            NormalizedMatchDefaults matchDefaults,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot,
            int displayOrder
    ) {
        String code = normalizedGroup.groupCode();
        List<TournamentParticipantEntity> seats = dualSeats(normalizedGroup, participantsBySlot);
        TournamentParticipantEntity seat1 = seats.get(0);
        TournamentParticipantEntity seat2 = seats.get(1);
        TournamentParticipantEntity seat3 = seats.get(2);
        TournamentParticipantEntity seat4 = seats.get(3);

        if (countActualParticipants(seats) == 2) {
            return createTwoParticipantDualGroupBracket(
                    stage,
                    group,
                    code,
                    bestOf,
                    mapDefaults,
                    matchDefaults,
                    displayOrder,
                    seat1,
                    seat2,
                    seat3,
                    seat4
            );
        }

        TournamentMatchEntity openingOne = saveDualMatchWithDefaults(stage, group, code + "1", TournamentMatchEntity.ROLE_OPENING, 1, 1, code + "1", bestOf, initialStatus(seat1, seat2), 1, 1, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity openingTwo = saveDualMatchWithDefaults(stage, group, code + "2", TournamentMatchEntity.ROLE_OPENING, 1, 2, code + "2", bestOf, initialStatus(seat3, seat4), 1, 2, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity winners = saveDualMatchWithDefaults(stage, group, code + "W", TournamentMatchEntity.ROLE_WINNERS, 2, 1, code + " Winners", bestOf, TournamentMatchEntity.STATUS_PENDING, 2, 1, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity losers = saveDualMatchWithDefaults(stage, group, code + "L", TournamentMatchEntity.ROLE_LOSERS, 2, 2, code + " Losers", bestOf, TournamentMatchEntity.STATUS_PENDING, 2, 2, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity decider = saveDualMatchWithDefaults(stage, group, code + "F", TournamentMatchEntity.ROLE_DECIDER, 3, 1, code + " Decider", bestOf, TournamentMatchEntity.STATUS_PENDING, 3, 1, displayOrder++, mapDefaults, matchDefaults);

        saveParticipantOrByeSlot(openingOne.getId(), 1, seat1);
        saveParticipantOrByeSlot(openingOne.getId(), 2, seat2);
        saveParticipantOrByeSlot(openingTwo.getId(), 1, seat3);
        saveParticipantOrByeSlot(openingTwo.getId(), 2, seat4);
        saveSourceSlot(winners.getId(), 1, openingOne.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, openingOne.getMatchKey() + " winner");
        saveSourceSlot(winners.getId(), 2, openingTwo.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, openingTwo.getMatchKey() + " winner");
        saveLoserSlotForDualOpening(losers.getId(), 1, openingOne, seat1, seat2);
        saveLoserSlotForDualOpening(losers.getId(), 2, openingTwo, seat3, seat4);
        saveSourceSlot(decider.getId(), 1, winners.getId(), TournamentMatchSlotEntity.OUTCOME_LOSER, winners.getMatchKey() + " loser");
        saveSourceSlot(decider.getId(), 2, losers.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, losers.getMatchKey() + " winner");

        DualQualifiedResultSlots resultSlots = saveDualQualifiedResultSlots(stage, group, code);

        saveMatchSlotRoute(openingOne.getId(), TournamentRouteEntity.OUTCOME_WINNER, winners.getId(), 1);
        saveMatchSlotRoute(openingTwo.getId(), TournamentRouteEntity.OUTCOME_WINNER, winners.getId(), 2);
        if (hasTwoActualParticipants(seat1, seat2)) {
            saveMatchSlotRoute(openingOne.getId(), TournamentRouteEntity.OUTCOME_LOSER, losers.getId(), 1);
        }
        if (hasTwoActualParticipants(seat3, seat4)) {
            saveMatchSlotRoute(openingTwo.getId(), TournamentRouteEntity.OUTCOME_LOSER, losers.getId(), 2);
        }
        saveResultSlotRoute(winners.getId(), TournamentRouteEntity.OUTCOME_WINNER, resultSlots.first().getId());
        saveMatchSlotRoute(winners.getId(), TournamentRouteEntity.OUTCOME_LOSER, decider.getId(), 1);
        saveMatchSlotRoute(losers.getId(), TournamentRouteEntity.OUTCOME_WINNER, decider.getId(), 2);
        saveEliminatedRoute(losers.getId(), TournamentRouteEntity.OUTCOME_LOSER);
        saveResultSlotRoute(decider.getId(), TournamentRouteEntity.OUTCOME_WINNER, resultSlots.second().getId());
        saveEliminatedRoute(decider.getId(), TournamentRouteEntity.OUTCOME_LOSER);

        return displayOrder;
    }

    private int createTwoParticipantDualGroupBracket(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            String code,
            int bestOf,
            NormalizedMapDefaults mapDefaults,
            NormalizedMatchDefaults matchDefaults,
            int displayOrder,
            TournamentParticipantEntity seat1,
            TournamentParticipantEntity seat2,
            TournamentParticipantEntity seat3,
            TournamentParticipantEntity seat4
    ) {
        TournamentMatchEntity openingOne = saveDualMatchWithDefaults(stage, group, code + "1", TournamentMatchEntity.ROLE_OPENING, 1, 1, code + "1", bestOf, initialStatus(seat1, seat2), 1, 1, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity openingTwo = saveDualMatchWithDefaults(stage, group, code + "2", TournamentMatchEntity.ROLE_OPENING, 1, 2, code + "2", bestOf, initialStatus(seat3, seat4), 1, 2, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity winners = saveDualMatchWithDefaults(stage, group, code + "W", TournamentMatchEntity.ROLE_WINNERS, 2, 1, code + " Winners", bestOf, TournamentMatchEntity.STATUS_PENDING, 2, 1, displayOrder++, mapDefaults, matchDefaults);
        TournamentMatchEntity decider = saveDualMatchWithDefaults(stage, group, code + "F", TournamentMatchEntity.ROLE_DECIDER, 3, 1, code + " Decider", bestOf, TournamentMatchEntity.STATUS_PENDING, 3, 1, displayOrder++, mapDefaults, matchDefaults);

        saveParticipantOrByeSlot(openingOne.getId(), 1, seat1);
        saveParticipantOrByeSlot(openingOne.getId(), 2, seat2);
        saveParticipantOrByeSlot(openingTwo.getId(), 1, seat3);
        saveParticipantOrByeSlot(openingTwo.getId(), 2, seat4);
        saveSourceSlot(winners.getId(), 1, openingOne.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, openingOne.getMatchKey() + " winner");
        saveSourceSlot(winners.getId(), 2, openingTwo.getId(), TournamentMatchSlotEntity.OUTCOME_WINNER, openingTwo.getMatchKey() + " winner");
        saveSourceSlot(decider.getId(), 1, winners.getId(), TournamentMatchSlotEntity.OUTCOME_LOSER, winners.getMatchKey() + " loser");
        saveParticipantOrByeSlot(decider.getId(), 2, null);

        DualQualifiedResultSlots resultSlots = saveDualQualifiedResultSlots(stage, group, code);

        saveMatchSlotRoute(openingOne.getId(), TournamentRouteEntity.OUTCOME_WINNER, winners.getId(), 1);
        saveMatchSlotRoute(openingTwo.getId(), TournamentRouteEntity.OUTCOME_WINNER, winners.getId(), 2);
        saveResultSlotRoute(winners.getId(), TournamentRouteEntity.OUTCOME_WINNER, resultSlots.first().getId());
        saveMatchSlotRoute(winners.getId(), TournamentRouteEntity.OUTCOME_LOSER, decider.getId(), 1);
        saveResultSlotRoute(decider.getId(), TournamentRouteEntity.OUTCOME_WINNER, resultSlots.second().getId());
        saveEliminatedRoute(decider.getId(), TournamentRouteEntity.OUTCOME_LOSER);

        return displayOrder;
    }

    private DualQualifiedResultSlots saveDualQualifiedResultSlots(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            String code
    ) {
        TournamentResultSlotEntity first = resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .resultKey(code + "_1ST")
                .resultType(TournamentResultSlotEntity.TYPE_QUALIFIED)
                .rankNo(1)
                .label(code + " 1st")
                .build());
        TournamentResultSlotEntity second = resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .resultKey(code + "_2ND")
                .resultType(TournamentResultSlotEntity.TYPE_QUALIFIED)
                .rankNo(2)
                .label(code + " 2nd")
                .build());

        return new DualQualifiedResultSlots(first, second);
    }

    private void createUltimateBattle(
            TournamentStageEntity stage,
            NormalizedTournament normalized,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot
    ) {
        NormalizedGroup normalizedGroup = normalized.groups().get(0);
        TournamentGroupEntity group = groupRepository.save(TournamentGroupEntity.builder()
                .stageId(stage.getId())
                .groupCode(normalizedGroup.groupCode())
                .groupName(normalizedGroup.groupName())
                .displayOrder(1)
                .build());
        createGroupEntries(group, normalizedGroup, participantsBySlot);

        List<TournamentParticipantEntity> participants = normalizedGroup.slots().stream()
                .map(slot -> participantsBySlot.get(slot.key()))
                .filter(Objects::nonNull)
                .toList();
        TournamentMatchEntity match = saveDualMatchWithDefaults(
                stage,
                group,
                "ULTIMATE",
                TournamentMatchEntity.ROLE_FINAL,
                1,
                1,
                "Ultimate Battle",
                normalized.bestOf(),
                TournamentMatchEntity.STATUS_READY,
                1,
                1,
                1,
                normalized.mapDefaults(),
                normalized.matchDefaults()
        );
        saveParticipantOrByeSlot(match.getId(), 1, participants.get(0));
        saveParticipantOrByeSlot(match.getId(), 2, participants.get(1));

        TournamentResultSlotEntity champion = resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .resultKey("CHAMPION")
                .resultType(TournamentResultSlotEntity.TYPE_CHAMPION)
                .rankNo(1)
                .label("Champion")
                .build());
        TournamentResultSlotEntity runnerUp = resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .resultKey("RUNNER_UP")
                .resultType(TournamentResultSlotEntity.TYPE_RUNNER_UP)
                .rankNo(2)
                .label("Runner-up")
                .build());
        saveResultSlotRoute(match.getId(), TournamentRouteEntity.OUTCOME_WINNER, champion.getId());
        saveResultSlotRoute(match.getId(), TournamentRouteEntity.OUTCOME_LOSER, runnerUp.getId());
    }

    private void createRaceSurvival(
            TournamentStageEntity stage,
            NormalizedTournament normalized,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot
    ) {
        for (NormalizedGroup normalizedGroup : normalized.groups()) {
            TournamentGroupEntity group = groupRepository.save(TournamentGroupEntity.builder()
                    .stageId(stage.getId())
                    .groupCode(normalizedGroup.groupCode())
                    .groupName(normalizedGroup.groupName())
                    .displayOrder(normalizedGroup.groupIndex() + 1)
                    .build());
            createGroupEntries(group, normalizedGroup, participantsBySlot);
        }

        TournamentGroupEntity matchesGroup = groupRepository.save(TournamentGroupEntity.builder()
                .stageId(stage.getId())
                .groupCode(MATCHES_GROUP_CODE)
                .groupName("Matches")
                .displayOrder(RACE_SURVIVAL_GROUP_CODES.size() + 1)
                .build());
        TournamentMatchEntity firstMatch = saveMatch(
                stage,
                matchesGroup,
                "M1",
                TournamentMatchEntity.ROLE_ROUND,
                1,
                1,
                "Match 1",
                1,
                TournamentMatchEntity.STATUS_PENDING,
                1,
                1,
                1
        );
        saveEmptyRaceSurvivalSlot(firstMatch.getId(), 1);
        saveEmptyRaceSurvivalSlot(firstMatch.getId(), 2);

        resultSlotRepository.save(TournamentResultSlotEntity.builder()
                .stageId(stage.getId())
                .groupId(matchesGroup.getId())
                .resultKey("CHAMPION")
                .resultType(TournamentResultSlotEntity.TYPE_CHAMPION)
                .rankNo(1)
                .label("Champion")
                .build());
    }

    private void createGroupEntries(
            TournamentGroupEntity group,
            NormalizedGroup normalizedGroup,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot
    ) {
        for (NormalizedSlot slot : normalizedGroup.slots()) {
            TournamentParticipantEntity participant = participantsBySlot.get(slot.key());
            groupEntryRepository.save(TournamentGroupEntryEntity.builder()
                    .groupId(group.getId())
                    .participantId(participant.getId())
                    .groupSeedNo(slot.slotNo())
                    .entryLabel(group.getGroupCode() + slot.slotNo())
                    .build());
        }
    }

    private List<TournamentParticipantEntity> dualSeats(
            NormalizedGroup normalizedGroup,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot
    ) {
        List<TournamentParticipantEntity> seats = new ArrayList<>();
        for (int index = 0; index < MAX_DUAL_GROUP_SIZE; index++) {
            seats.add(null);
        }
        for (NormalizedSlot slot : normalizedGroup.slots()) {
            seats.set(slot.slotNo() - 1, participantsBySlot.get(slot.key()));
        }
        return seats;
    }

    private int countActualParticipants(List<TournamentParticipantEntity> seats) {
        return (int) seats.stream()
                .filter(Objects::nonNull)
                .count();
    }

    private List<TournamentParticipantEntity> distributeSingleEliminationByes(
            List<TournamentParticipantEntity> participants,
            int bracketSize
    ) {
        int byeCount = bracketSize - participants.size();
        int matchCount = bracketSize / 2;
        List<TournamentParticipantEntity> slots = new ArrayList<>(bracketSize);
        int participantIndex = 0;
        for (int matchIndex = 0; matchIndex < matchCount; matchIndex++) {
            slots.add(participants.get(participantIndex++));
            if (matchIndex < byeCount) {
                slots.add(null);
            } else {
                slots.add(participants.get(participantIndex++));
            }
        }
        return slots;
    }

    private TournamentMatchEntity saveMatch(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            String matchKey,
            String matchRole,
            int roundNo,
            int matchNo,
            String displayName,
            int bestOf,
            String status,
            int layoutCol,
            int layoutRow,
            int displayOrder
    ) {
        return saveMatch(
                stage,
                group,
                matchKey,
                matchRole,
                roundNo,
                matchNo,
                displayName,
                bestOf,
                status,
                layoutCol,
                layoutRow,
                displayOrder,
                null
        );
    }

    private TournamentMatchEntity saveDualMatchWithDefaults(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            String matchKey,
            String matchRole,
            int roundNo,
            int matchNo,
            String displayName,
            int fallbackBestOf,
            String status,
            int layoutCol,
            int layoutRow,
            int displayOrder,
            NormalizedMapDefaults mapDefaults,
            NormalizedMatchDefaults matchDefaults
    ) {
        List<Long> mapIds = matchDefaults.mapIdsForRole(matchRole);
        Long legacyMapId = mapDefaults.mapForRole(matchRole);
        TournamentMatchEntity match = saveMatch(
                stage,
                group,
                matchKey,
                matchRole,
                roundNo,
                matchNo,
                displayName,
                matchDefaults.bestOfForRole(matchRole, fallbackBestOf),
                status,
                layoutCol,
                layoutRow,
                displayOrder,
                firstMapId(mapIds, legacyMapId)
        );
        if (!mapIds.isEmpty()) {
            saveMatchSetDefaults(match.getId(), mapIds);
        } else if (legacyMapId != null) {
            saveMatchSetDefaults(match.getId(), List.of(legacyMapId));
        }
        return match;
    }

    private TournamentMatchEntity saveMatch(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            String matchKey,
            String matchRole,
            int roundNo,
            int matchNo,
            String displayName,
            int bestOf,
            String status,
            int layoutCol,
            int layoutRow,
            int displayOrder,
            Long mapId
    ) {
        return matchRepository.save(TournamentMatchEntity.builder()
                .stageId(stage.getId())
                .groupId(group.getId())
                .matchKey(matchKey)
                .matchRole(matchRole)
                .roundNo(roundNo)
                .matchNo(matchNo)
                .displayName(displayName)
                .bestOf(bestOf)
                .status(status)
                .mapId(mapId)
                .layoutCol(layoutCol)
                .layoutRow(layoutRow)
                .displayOrder(displayOrder)
                .build());
    }

    private void saveMatchSetDefaults(Long matchId, List<Long> mapIds) {
        for (int index = 0; index < mapIds.size(); index++) {
            matchSetRepository.save(TournamentMatchSetEntity.builder()
                    .matchId(matchId)
                    .setNo(index + 1)
                    .mapId(mapIds.get(index))
                    .build());
        }
    }

    private Long firstMapId(List<Long> mapIds, Long fallbackMapId) {
        return mapIds.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallbackMapId);
    }

    private void saveParticipantOrByeSlot(Long matchId, int slotNo, TournamentParticipantEntity participant) {
        if (participant == null) {
            matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                    .matchId(matchId)
                    .slotNo(slotNo)
                    .placeholderLabel(BYE_LABEL)
                    .isWinner(0)
                    .isBye(1)
                    .build());
            return;
        }

        matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                .matchId(matchId)
                .slotNo(slotNo)
                .participantId(participant.getId())
                .isWinner(0)
                .isBye(0)
                .build());
    }

    private void saveEmptyRaceSurvivalSlot(Long matchId, int slotNo) {
        matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                .matchId(matchId)
                .slotNo(slotNo)
                .placeholderLabel(RACE_SURVIVAL_EMPTY_SLOT_LABEL)
                .isWinner(0)
                .isBye(0)
                .build());
    }

    private void saveSourceSlot(Long matchId, int slotNo, Long sourceMatchId, String sourceOutcome, String placeholderLabel) {
        matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                .matchId(matchId)
                .slotNo(slotNo)
                .sourceMatchId(sourceMatchId)
                .sourceOutcome(sourceOutcome)
                .placeholderLabel(placeholderLabel)
                .isWinner(0)
                .isBye(0)
                .build());
    }

    private void saveLoserSlotForDualOpening(
            Long matchId,
            int slotNo,
            TournamentMatchEntity sourceMatch,
            TournamentParticipantEntity first,
            TournamentParticipantEntity second
    ) {
        if (hasTwoActualParticipants(first, second)) {
            saveSourceSlot(matchId, slotNo, sourceMatch.getId(), TournamentMatchSlotEntity.OUTCOME_LOSER, sourceMatch.getMatchKey() + " loser");
            return;
        }
        saveParticipantOrByeSlot(matchId, slotNo, null);
    }

    private void saveMatchSlotRoute(Long fromMatchId, String outcome, Long toMatchId, int toSlotNo) {
        routeRepository.save(TournamentRouteEntity.builder()
                .fromMatchId(fromMatchId)
                .outcome(outcome)
                .targetType(TournamentRouteEntity.TARGET_MATCH_SLOT)
                .toMatchId(toMatchId)
                .toSlotNo(toSlotNo)
                .build());
    }

    private void saveResultSlotRoute(Long fromMatchId, String outcome, Long toResultSlotId) {
        routeRepository.save(TournamentRouteEntity.builder()
                .fromMatchId(fromMatchId)
                .outcome(outcome)
                .targetType(TournamentRouteEntity.TARGET_RESULT_SLOT)
                .toResultSlotId(toResultSlotId)
                .build());
    }

    private void saveEliminatedRoute(Long fromMatchId, String outcome) {
        routeRepository.save(TournamentRouteEntity.builder()
                .fromMatchId(fromMatchId)
                .outcome(outcome)
                .targetType(TournamentRouteEntity.TARGET_ELIMINATED)
                .build());
    }

    private String initialStatus(TournamentParticipantEntity first, TournamentParticipantEntity second) {
        if (first != null || second != null) {
            return TournamentMatchEntity.STATUS_READY;
        }
        return TournamentMatchEntity.STATUS_PENDING;
    }

    private boolean hasTwoActualParticipants(TournamentParticipantEntity first, TournamentParticipantEntity second) {
        return first != null && second != null;
    }

    private int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result *= 2;
        }
        return result;
    }

    private String normalizeBracketType(String bracketType) {
        String normalized = requireText(bracketType, "bracketType is required.").toUpperCase();
        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(normalized)
                || TournamentStageEntity.TYPE_DUAL_GROUP.equals(normalized)
                || TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(normalized)
                || TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("bracketType must be SINGLE_ELIMINATION, DUAL_GROUP, ULTIMATE_BATTLE, or RACE_SURVIVAL.");
    }

    private int normalizeBestOf(Integer bestOf) {
        int normalized = bestOf == null ? DEFAULT_BEST_OF : bestOf;
        if (normalized < 1 || normalized % 2 == 0) {
            throw new IllegalArgumentException("bestOf must be a positive odd number.");
        }
        return normalized;
    }

    private String normalizeGroupCode(
            TournamentCreateGroupRequestDto request,
            int groupIndex,
            String bracketType
    ) {
        String requestedCode = request == null ? null : trimToNull(request.getGroupCode());
        if (requestedCode != null) {
            return requestedCode.toUpperCase();
        }
        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)) {
            return MAIN_GROUP_CODE;
        }
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            return MAIN_GROUP_CODE;
        }
        if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(bracketType)) {
            if (groupIndex >= 0 && groupIndex < RACE_SURVIVAL_GROUP_CODES.size()) {
                return RACE_SURVIVAL_GROUP_CODES.get(groupIndex);
            }
            return "RACE" + (groupIndex + 1);
        }
        return defaultDualGroupCode(groupIndex);
    }

    private String normalizeGroupName(
            TournamentCreateGroupRequestDto request,
            String groupCode,
            String bracketType
    ) {
        String requestedName = request == null ? null : trimToNull(request.getGroupName());
        if (requestedName != null) {
            return requestedName;
        }
        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)) {
            return MAIN_GROUP_NAME;
        }
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            return "Ultimate Battle";
        }
        if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(bracketType)) {
            return groupCode;
        }
        return groupCode + " Group";
    }

    private String defaultDualGroupCode(int groupIndex) {
        if (groupIndex < 26) {
            return String.valueOf((char) ('A' + groupIndex));
        }
        return "G" + (groupIndex + 1);
    }

    private String defaultStageName(String bracketType) {
        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(bracketType)) {
            return MAIN_GROUP_NAME;
        }
        if (TournamentStageEntity.TYPE_ULTIMATE_BATTLE.equals(bracketType)) {
            return "Ultimate Battle";
        }
        if (TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(bracketType)) {
            return "Race Survival";
        }
        return "Dual Group";
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NormalizedTournament(
            String title,
            String bracketType,
            int bestOf,
            List<NormalizedGroup> groups,
            NormalizedMapDefaults mapDefaults,
            NormalizedMatchDefaults matchDefaults
    ) {
    }

    private record NormalizedMapDefaults(
            Map<Integer, Long> roundMapIds,
            Map<String, Long> roleMapIds
    ) {
        private static NormalizedMapDefaults empty() {
            return new NormalizedMapDefaults(Map.of(), Map.of());
        }

        private Long mapForRound(Integer roundNo) {
            return roundMapIds.get(roundNo);
        }

        private Long mapForRole(String matchRole) {
            return roleMapIds.get(matchRole);
        }
    }

    private record NormalizedMatchDefaults(
            Map<Integer, MatchDefaultConfig> roundDefaults,
            Map<String, MatchDefaultConfig> roleDefaults
    ) {
        private static NormalizedMatchDefaults empty() {
            return new NormalizedMatchDefaults(Map.of(), Map.of());
        }

        private int bestOfForRound(Integer roundNo, int fallbackBestOf) {
            MatchDefaultConfig config = roundDefaults.get(roundNo);
            return config == null ? fallbackBestOf : config.effectiveBestOf(fallbackBestOf);
        }

        private int bestOfForRole(String matchRole, int fallbackBestOf) {
            MatchDefaultConfig config = roleDefaults.get(matchRole);
            return config == null ? fallbackBestOf : config.effectiveBestOf(fallbackBestOf);
        }

        private List<Long> mapIdsForRound(Integer roundNo) {
            MatchDefaultConfig config = roundDefaults.get(roundNo);
            return config == null ? List.of() : config.mapIds();
        }

        private List<Long> mapIdsForRole(String matchRole) {
            MatchDefaultConfig config = roleDefaults.get(matchRole);
            return config == null ? List.of() : config.mapIds();
        }
    }

    private record MatchDefaultConfig(Integer bestOf, List<Long> mapIds) {
        private int effectiveBestOf(int fallbackBestOf) {
            return bestOf == null ? fallbackBestOf : bestOf;
        }

        private Long firstMapId() {
            return mapIds.stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
    }

    private record DualQualifiedResultSlots(
            TournamentResultSlotEntity first,
            TournamentResultSlotEntity second
    ) {
    }

    private record NormalizedGroup(
            int groupIndex,
            String groupCode,
            String groupName,
            List<NormalizedSlot> slots
    ) {
    }

    private record NormalizedSlot(
            SlotKey key,
            int slotNo,
            Long userId,
            String participantName
    ) {
    }

    private record SlotKey(int groupIndex, int slotNo) {
    }
}
