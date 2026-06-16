package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogStatusResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentClanShareSendLogSummaryResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentClanShareSendLogEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSetEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentClanShareSendLogRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSetRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentClanShareSendLogService {

    private static final int MESSAGE_MAX_LENGTH = 500;
    private static final String STATUS_UNSENT = "UNSENT";

    private final TournamentRepository tournamentRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentGroupRepository groupRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentMatchSetRepository matchSetRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentClanShareSendLogRepository logRepository;
    private final MapRepository mapRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TournamentClanShareSendLogSummaryResponseDto getSummary(Long tournamentId) {
        requireTournament(tournamentId);
        long totalCount = logRepository.countByTournamentId(tournamentId);

        return TournamentClanShareSendLogSummaryResponseDto.builder()
                .hasHistory(totalCount > 0)
                .totalCount(totalCount)
                .latestSentAt(logRepository.findFirstByTournamentIdOrderByRegDateDescIdDesc(tournamentId)
                        .map(TournamentClanShareSendLogEntity::getRegDate)
                        .orElse(null))
                .build();
    }

    @Transactional(readOnly = true)
    public TournamentClanShareSendLogStatusResponseDto getStatus(Long tournamentId) {
        requireTournament(tournamentId);
        TournamentStageEntity stage = findClanShareManagedStage(tournamentId);
        List<TournamentGroupEntity> groups = groupRepository.findAllByStageIdOrderByDisplayOrderAsc(stage.getId());
        List<Long> groupIds = groups.stream()
                .map(TournamentGroupEntity::getId)
                .toList();
        if (groupIds.isEmpty()) {
            return emptyStatus();
        }

        Map<Long, TournamentGroupEntity> groupsById = groups.stream()
                .collect(Collectors.toMap(TournamentGroupEntity::getId, Function.identity()));
        List<TournamentMatchEntity> matches = matchRepository.findAllByGroupIdInOrderByDisplayOrderAsc(groupIds).stream()
                .sorted(Comparator
                        .comparing((TournamentMatchEntity match) -> nullsLast(match.getDisplayOrder()))
                        .thenComparing(TournamentMatchEntity::getId))
                .toList();
        List<Long> matchIds = matches.stream()
                .map(TournamentMatchEntity::getId)
                .toList();
        Map<Long, List<TournamentMatchSlotEntity>> slotsByMatchId = loadSlotsByMatchId(matchIds);
        Map<Long, List<TournamentMatchSetEntity>> setsByMatchId = loadSetsByMatchId(matchIds);
        Map<Long, TournamentParticipantEntity> participantsById = loadParticipants(slotsByMatchId);
        Map<Long, UserEntity> usersById = loadUsers(participantsById.values());
        Map<Long, String> mapNamesById = loadMapNames(matches, setsByMatchId);
        Map<Long, List<TournamentClanShareSendLogEntity>> logsByMatchId = loadLogsByMatchId(tournamentId, matchIds);
        List<TournamentMatchEntity> completedMatches = matches.stream()
                .filter(match -> isCompletedActualMatch(match, slotsByMatchId.getOrDefault(match.getId(), List.of())))
                .toList();

        LinkedHashMap<String, StatusGroupState> statusGroups = new LinkedHashMap<>();
        Integer finalRoundNo = completedMatches.stream()
                .map(TournamentMatchEntity::getRoundNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
        StatusCounter totals = new StatusCounter();

        for (TournamentMatchEntity match : completedMatches) {
            String groupKey = buildGroupKey(stage, match, groupsById);
            String groupLabel = buildGroupLabel(stage, match, groupsById, finalRoundNo);
            StatusGroupState statusGroup = statusGroups.computeIfAbsent(
                    groupKey,
                    ignored -> new StatusGroupState(groupLabel)
            );
            TournamentClanShareSendLogStatusResponseDto.Match statusMatch = toStatusMatch(
                    match,
                    slotsByMatchId.getOrDefault(match.getId(), List.of()),
                    setsByMatchId.getOrDefault(match.getId(), List.of()),
                    participantsById,
                    usersById,
                    mapNamesById,
                    logsByMatchId.getOrDefault(match.getId(), List.of())
            );
            statusGroup.matches().add(statusMatch);
            totals.add(statusMatch);
        }

        List<TournamentClanShareSendLogStatusResponseDto.Group> responseGroups = statusGroups.entrySet().stream()
                .map(entry -> TournamentClanShareSendLogStatusResponseDto.Group.builder()
                        .groupKey(entry.getKey())
                        .groupLabel(entry.getValue().label())
                        .matches(entry.getValue().matches())
                        .build())
                .toList();

        return TournamentClanShareSendLogStatusResponseDto.builder()
                .groups(responseGroups)
                .totals(totals.toDto())
                .build();
    }

    @Transactional
    public TournamentClanShareSendLogResponseDto createLog(
            TournamentClanShareSendLogRequestDto request,
            Long requestedByUserId
    ) {
        if (requestedByUserId == null) {
            throw invalid("requestedByUserId is required.");
        }
        validateRequest(request);
        validateMatchInTournament(request.getTournamentId(), request.getMatchId());

        TournamentClanShareSendLogEntity saved = logRepository.save(TournamentClanShareSendLogEntity.builder()
                .tournamentId(request.getTournamentId())
                .matchId(request.getMatchId())
                .sendGroupId(trimRequired(request.getSendGroupId(), "sendGroupId"))
                .player1(trimRequired(request.getPlayer1(), "player1"))
                .player2(trimRequired(request.getPlayer2(), "player2"))
                .winner(trimRequired(request.getWinner(), "winner"))
                .loser(trimRequired(request.getLoser(), "loser"))
                .mapName(trimRequired(request.getMapName(), "mapName"))
                .matchType(trimRequired(request.getMatchType(), "matchType"))
                .matchName(trimRequired(request.getMatchName(), "matchName"))
                .playedDate(trimRequired(request.getPlayedDate(), "playedDate"))
                .eloStatus(trimRequired(request.getEloStatus(), "eloStatus"))
                .eloMessage(trimOptional(request.getEloMessage()))
                .sheetStatus(trimRequired(request.getSheetStatus(), "sheetStatus"))
                .sheetMessage(trimOptional(request.getSheetMessage()))
                .requestedByUserId(requestedByUserId)
                .build());

        return toResponse(saved);
    }

    private TournamentStageEntity findClanShareManagedStage(Long tournamentId) {
        return stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournamentId).stream()
                .filter(stage -> TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(stage.getStageType())
                        || TournamentStageEntity.TYPE_DUAL_GROUP.equals(stage.getStageType()))
                .findFirst()
                .orElseThrow(() -> invalid("Clan-share status is only supported for single elimination and dual group tournaments."));
    }

    private TournamentClanShareSendLogStatusResponseDto emptyStatus() {
        return TournamentClanShareSendLogStatusResponseDto.builder()
                .groups(List.of())
                .totals(TournamentClanShareSendLogStatusResponseDto.Totals.builder()
                        .total(0)
                        .success(0)
                        .failed(0)
                        .unsent(0)
                        .sheetFailed(0)
                        .retryable(0)
                        .build())
                .build();
    }

    private Map<Long, List<TournamentMatchSlotEntity>> loadSlotsByMatchId(List<Long> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }

        return matchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(matchIds).stream()
                .sorted(Comparator
                        .comparing((TournamentMatchSlotEntity slot) -> nullsLast(slot.getSlotNo()))
                        .thenComparing(TournamentMatchSlotEntity::getId))
                .collect(Collectors.groupingBy(TournamentMatchSlotEntity::getMatchId));
    }

    private Map<Long, List<TournamentMatchSetEntity>> loadSetsByMatchId(List<Long> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }

        return matchSetRepository.findAllByMatchIdInOrderByMatchIdAscSetNoAsc(matchIds).stream()
                .sorted(Comparator
                        .comparing((TournamentMatchSetEntity set) -> nullsLast(set.getSetNo()))
                        .thenComparing(TournamentMatchSetEntity::getId))
                .collect(Collectors.groupingBy(TournamentMatchSetEntity::getMatchId));
    }

    private Map<Long, TournamentParticipantEntity> loadParticipants(
            Map<Long, List<TournamentMatchSlotEntity>> slotsByMatchId
    ) {
        Set<Long> participantIds = slotsByMatchId.values().stream()
                .flatMap(List::stream)
                .map(TournamentMatchSlotEntity::getParticipantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (participantIds.isEmpty()) {
            return Map.of();
        }

        return participantRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(TournamentParticipantEntity::getId, Function.identity()));
    }

    private Map<Long, UserEntity> loadUsers(Iterable<TournamentParticipantEntity> participants) {
        Set<Long> userIds = new HashSet<>();
        for (TournamentParticipantEntity participant : participants) {
            if (participant.getUserId() != null) {
                userIds.add(participant.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private Map<Long, String> loadMapNames(
            List<TournamentMatchEntity> matches,
            Map<Long, List<TournamentMatchSetEntity>> setsByMatchId
    ) {
        Set<Long> mapIds = matches.stream()
                .map(TournamentMatchEntity::getMapId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        setsByMatchId.values().stream()
                .flatMap(List::stream)
                .map(TournamentMatchSetEntity::getMapId)
                .filter(Objects::nonNull)
                .forEach(mapIds::add);
        if (mapIds.isEmpty()) {
            return Map.of();
        }

        return mapRepository.findAllById(mapIds).stream()
                .collect(Collectors.toMap(MapEntity::getId, MapEntity::getMapName));
    }

    private Map<Long, List<TournamentClanShareSendLogEntity>> loadLogsByMatchId(
            Long tournamentId,
            List<Long> matchIds
    ) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }

        return logRepository.findAllByTournamentIdAndMatchIdInOrderByRegDateDescIdDesc(tournamentId, matchIds)
                .stream()
                .collect(Collectors.groupingBy(
                        TournamentClanShareSendLogEntity::getMatchId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private boolean isCompletedActualMatch(TournamentMatchEntity match, List<TournamentMatchSlotEntity> slots) {
        if (!TournamentMatchEntity.STATUS_FINISHED.equals(match.getStatus())) {
            return false;
        }
        List<TournamentMatchSlotEntity> playableSlots = slots.stream()
                .filter(slot -> slot.getParticipantId() != null)
                .filter(slot -> !Integer.valueOf(1).equals(slot.getIsBye()))
                .toList();

        return playableSlots.size() == 2
                && playableSlots.stream().anyMatch(slot -> Integer.valueOf(1).equals(slot.getIsWinner()));
    }

    private String buildGroupKey(
            TournamentStageEntity stage,
            TournamentMatchEntity match,
            Map<Long, TournamentGroupEntity> groupsById
    ) {
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(stage.getStageType())) {
            TournamentGroupEntity group = groupsById.get(match.getGroupId());
            String groupCode = group == null ? "UNKNOWN" : group.getGroupCode();
            return "DUAL:" + groupCode + ":" + match.getMatchRole();
        }

        return "ROUND:" + (match.getRoundNo() == null ? 0 : match.getRoundNo());
    }

    private String buildGroupLabel(
            TournamentStageEntity stage,
            TournamentMatchEntity match,
            Map<Long, TournamentGroupEntity> groupsById,
            Integer finalRoundNo
    ) {
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(stage.getStageType())) {
            TournamentGroupEntity group = groupsById.get(match.getGroupId());
            String groupCode = group == null ? "?" : group.getGroupCode();
            return groupCode + "조 " + matchRoleLabel(match.getMatchRole());
        }

        if (TournamentMatchEntity.ROLE_FINAL.equals(match.getMatchRole())
                || Objects.equals(match.getRoundNo(), finalRoundNo)) {
            return "Final";
        }
        return "Round " + (match.getRoundNo() == null ? 0 : match.getRoundNo());
    }

    private String matchRoleLabel(String matchRole) {
        if (TournamentMatchEntity.ROLE_OPENING.equals(matchRole)) {
            return "첫세트";
        }
        if (TournamentMatchEntity.ROLE_WINNERS.equals(matchRole)) {
            return "승자전";
        }
        if (TournamentMatchEntity.ROLE_LOSERS.equals(matchRole)) {
            return "패자전";
        }
        if (TournamentMatchEntity.ROLE_DECIDER.equals(matchRole)) {
            return "최종전";
        }
        if (TournamentMatchEntity.ROLE_FINAL.equals(matchRole)) {
            return "결승";
        }
        return matchRole == null ? "경기" : matchRole;
    }

    private TournamentClanShareSendLogStatusResponseDto.Match toStatusMatch(
            TournamentMatchEntity match,
            List<TournamentMatchSlotEntity> slots,
            List<TournamentMatchSetEntity> sets,
            Map<Long, TournamentParticipantEntity> participantsById,
            Map<Long, UserEntity> usersById,
            Map<Long, String> mapNamesById,
            List<TournamentClanShareSendLogEntity> logs
    ) {
        TournamentMatchSlotEntity slot1 = findSlot(slots, 1);
        TournamentMatchSlotEntity slot2 = findSlot(slots, 2);
        TournamentMatchSlotEntity winnerSlot = slots.stream()
                .filter(slot -> Integer.valueOf(1).equals(slot.getIsWinner()))
                .findFirst()
                .orElse(null);
        TournamentClanShareSendLogEntity successLog = logs.stream()
                .filter(log -> TournamentClanShareSendLogEntity.STATUS_SUCCESS.equals(log.getEloStatus()))
                .findFirst()
                .orElse(null);
        TournamentClanShareSendLogEntity latestLog = logs.isEmpty() ? null : logs.get(0);
        TournamentClanShareSendLogEntity statusLog = successLog != null ? successLog : latestLog;
        String status = resolveStatus(successLog, latestLog);

        return TournamentClanShareSendLogStatusResponseDto.Match.builder()
                .matchId(match.getId())
                .player1(resolveSlotName(slot1, participantsById, usersById))
                .player2(resolveSlotName(slot2, participantsById, usersById))
                .winner(resolveSlotName(winnerSlot, participantsById, usersById))
                .mapName(resolveMapName(match, sets, mapNamesById, statusLog))
                .status(status)
                .eloMessage(statusLog == null ? null : statusLog.getEloMessage())
                .sheetStatus(statusLog == null ? null : statusLog.getSheetStatus())
                .sheetMessage(statusLog == null ? null : statusLog.getSheetMessage())
                .latestSentAt(statusLog == null ? null : statusLog.getRegDate())
                .retryable(!TournamentClanShareSendLogEntity.STATUS_SUCCESS.equals(status))
                .build();
    }

    private TournamentMatchSlotEntity findSlot(List<TournamentMatchSlotEntity> slots, int slotNo) {
        return slots.stream()
                .filter(slot -> Objects.equals(slot.getSlotNo(), slotNo))
                .findFirst()
                .orElseGet(() -> slots.size() >= slotNo ? slots.get(slotNo - 1) : null);
    }

    private String resolveStatus(
            TournamentClanShareSendLogEntity successLog,
            TournamentClanShareSendLogEntity latestLog
    ) {
        if (successLog != null) {
            return TournamentClanShareSendLogEntity.STATUS_SUCCESS;
        }
        if (latestLog != null && TournamentClanShareSendLogEntity.STATUS_FAILED.equals(latestLog.getEloStatus())) {
            return TournamentClanShareSendLogEntity.STATUS_FAILED;
        }
        return STATUS_UNSENT;
    }

    private String resolveSlotName(
            TournamentMatchSlotEntity slot,
            Map<Long, TournamentParticipantEntity> participantsById,
            Map<Long, UserEntity> usersById
    ) {
        if (slot == null || slot.getParticipantId() == null) {
            return "";
        }
        TournamentParticipantEntity participant = participantsById.get(slot.getParticipantId());
        if (participant == null) {
            return "";
        }
        UserEntity user = participant.getUserId() == null ? null : usersById.get(participant.getUserId());
        if (user != null && user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId();
        }
        return participant.getParticipantName();
    }

    private String resolveMapName(
            TournamentMatchEntity match,
            List<TournamentMatchSetEntity> sets,
            Map<Long, String> mapNamesById,
            TournamentClanShareSendLogEntity statusLog
    ) {
        String setMapSummary = sets.stream()
                .map(TournamentMatchSetEntity::getMapId)
                .filter(Objects::nonNull)
                .map(mapNamesById::get)
                .filter(Objects::nonNull)
                .filter(mapName -> !mapName.isBlank())
                .distinct()
                .collect(Collectors.joining(" / "));
        if (!setMapSummary.isBlank()) {
            return setMapSummary;
        }
        if (match.getMapId() != null) {
            String matchMapName = mapNamesById.get(match.getMapId());
            if (matchMapName != null && !matchMapName.isBlank()) {
                return matchMapName;
            }
        }
        return statusLog == null ? "" : statusLog.getMapName();
    }

    private Integer nullsLast(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private record StatusGroupState(
            String label,
            List<TournamentClanShareSendLogStatusResponseDto.Match> matches
    ) {
        private StatusGroupState(String label) {
            this(label, new ArrayList<>());
        }
    }

    private static class StatusCounter {
        private int total;
        private int success;
        private int failed;
        private int unsent;
        private int sheetFailed;
        private int retryable;

        private void add(TournamentClanShareSendLogStatusResponseDto.Match match) {
            total++;
            if (TournamentClanShareSendLogEntity.STATUS_SUCCESS.equals(match.getStatus())) {
                success++;
            } else if (TournamentClanShareSendLogEntity.STATUS_FAILED.equals(match.getStatus())) {
                failed++;
            } else {
                unsent++;
            }
            if (TournamentClanShareSendLogEntity.STATUS_FAILED.equals(match.getSheetStatus())) {
                sheetFailed++;
            }
            if (match.isRetryable()) {
                retryable++;
            }
        }

        private TournamentClanShareSendLogStatusResponseDto.Totals toDto() {
            return TournamentClanShareSendLogStatusResponseDto.Totals.builder()
                    .total(total)
                    .success(success)
                    .failed(failed)
                    .unsent(unsent)
                    .sheetFailed(sheetFailed)
                    .retryable(retryable)
                    .build();
        }
    }

    private void validateRequest(TournamentClanShareSendLogRequestDto request) {
        if (request == null) {
            throw invalid("Request body is required.");
        }
        if (request.getTournamentId() == null) {
            throw invalid("tournamentId is required.");
        }
        if (request.getMatchId() == null) {
            throw invalid("matchId is required.");
        }
        requireValidStatus(request.getEloStatus(), "eloStatus");
        requireValidStatus(request.getSheetStatus(), "sheetStatus");
        trimRequired(request.getSendGroupId(), "sendGroupId");
        trimRequired(request.getPlayer1(), "player1");
        trimRequired(request.getPlayer2(), "player2");
        trimRequired(request.getWinner(), "winner");
        trimRequired(request.getLoser(), "loser");
        trimRequired(request.getMapName(), "mapName");
        trimRequired(request.getMatchType(), "matchType");
        trimRequired(request.getMatchName(), "matchName");
        trimRequired(request.getPlayedDate(), "playedDate");
    }

    private void validateMatchInTournament(Long tournamentId, Long matchId) {
        requireTournament(tournamentId);
        TournamentMatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> notFound("Match not found."));
        TournamentStageEntity stage = stageRepository.findById(match.getStageId())
                .orElseThrow(() -> notFound("Tournament stage not found."));
        if (!Objects.equals(stage.getTournamentId(), tournamentId)) {
            throw notFound("Match not found in tournament.");
        }
    }

    private void requireTournament(Long tournamentId) {
        if (tournamentId == null) {
            throw invalid("tournamentId is required.");
        }
        if (!tournamentRepository.existsById(tournamentId)) {
            throw notFound("Tournament not found.");
        }
    }

    private void requireValidStatus(String status, String fieldName) {
        String normalized = trimRequired(status, fieldName);
        if (!TournamentClanShareSendLogEntity.STATUS_SUCCESS.equals(normalized)
                && !TournamentClanShareSendLogEntity.STATUS_FAILED.equals(normalized)) {
            throw invalid(fieldName + " must be SUCCESS or FAILED.");
        }
    }

    private String trimRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(fieldName + " is required.");
        }
        return value.trim();
    }

    private String trimOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().substring(0, Math.min(value.trim().length(), MESSAGE_MAX_LENGTH));
    }

    private TournamentClanShareSendLogResponseDto toResponse(TournamentClanShareSendLogEntity entity) {
        return TournamentClanShareSendLogResponseDto.builder()
                .id(entity.getId())
                .tournamentId(entity.getTournamentId())
                .matchId(entity.getMatchId())
                .sendGroupId(entity.getSendGroupId())
                .player1(entity.getPlayer1())
                .player2(entity.getPlayer2())
                .winner(entity.getWinner())
                .loser(entity.getLoser())
                .mapName(entity.getMapName())
                .matchType(entity.getMatchType())
                .matchName(entity.getMatchName())
                .playedDate(entity.getPlayedDate())
                .eloStatus(entity.getEloStatus())
                .eloMessage(entity.getEloMessage())
                .sheetStatus(entity.getSheetStatus())
                .sheetMessage(entity.getSheetMessage())
                .requestedByUserId(entity.getRequestedByUserId())
                .regDate(entity.getRegDate())
                .build();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private NoSuchElementException notFound(String message) {
        return new NoSuchElementException(message);
    }
}
