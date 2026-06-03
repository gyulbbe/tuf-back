package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentGroupResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentMatchResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentMatchSlotResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentParticipantResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentPageResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentResultSlotResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentStageResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentSummaryResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchScoreSubmissionEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentRouteRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.tournament.repository.RaceSurvivalProgressSubmissionMatchRepository;
import io.github.gyulbbe.tournament.repository.RaceSurvivalProgressSubmissionRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentService {

    private static final Set<String> ADMIN_ROLES = Set.of("ROLE_MANAGER", "ROLE_MASTER", "ROLE_ADMIN");

    private static final List<String> PUBLIC_STATUSES = List.of(
            TournamentEntity.STATUS_LIVE,
            TournamentEntity.STATUS_FINISHED
    );
    private static final List<String> RACE_ORDER = List.of("TERRAN", "ZERG", "PROTOSS");
    private static final String RACE_SURVIVAL_EMPTY_SLOT_LABEL = "선수 지정";

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentGroupRepository groupRepository;
    private final TournamentGroupEntryRepository groupEntryRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentResultSlotRepository resultSlotRepository;
    private final TournamentRouteRepository routeRepository;
    private final RaceSurvivalProgressSubmissionRepository raceSurvivalProgressSubmissionRepository;
    private final RaceSurvivalProgressSubmissionMatchRepository raceSurvivalProgressSubmissionMatchRepository;
    private final UserRepository userRepository;
    private final MapRepository mapRepository;

    public ResponseDto<List<TournamentSummaryResponseDto>> listPublicTournaments() {
        try {
            List<TournamentSummaryResponseDto> response = tournamentRepository
                    .findAllByStatusInOrderByUpdateDateDescRegDateDesc(PUBLIC_STATUSES)
                    .stream()
                    .map(this::toSummary)
                    .toList();

            return ResponseDto.success(response);
        } catch (Exception e) {
            log.warn("Failed to list public tournaments.", e);
            return ResponseDto.fail("토너먼트 목록 조회에 실패했습니다.");
        }
    }

    public ResponseDto<TournamentPageResponseDto> listPublicTournaments(int page, int size, String keyword) {
        try {
            int normalizedPage = Math.max(page, 0);
            int normalizedSize = Math.min(Math.max(size, 1), 50);
            String normalizedKeyword = normalizeKeyword(keyword);
            Page<TournamentEntity> tournamentPage = tournamentRepository.findPublicPage(
                    PUBLIC_STATUSES,
                    TournamentEntity.STATUS_LIVE,
                    normalizedKeyword,
                    PageRequest.of(normalizedPage, normalizedSize)
            );
            List<TournamentSummaryResponseDto> items = tournamentPage.getContent().stream()
                    .map(this::toSummary)
                    .toList();

            return ResponseDto.success(TournamentPageResponseDto.builder()
                    .items(items)
                    .page(tournamentPage.getNumber())
                    .size(tournamentPage.getSize())
                    .totalElements(tournamentPage.getTotalElements())
                    .totalPages(tournamentPage.getTotalPages())
                    .hasNext(tournamentPage.hasNext())
                    .hasPrevious(tournamentPage.hasPrevious())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to list public tournaments. page={}, size={}, keyword={}", page, size, keyword, e);
            return ResponseDto.fail("토너먼트 목록 조회에 실패했습니다.");
        }
    }

    @Transactional
    public ResponseDto<Void> deleteTournaments(List<Long> tournamentIds) {
        List<Long> normalizedIds = normalizeTournamentIds(tournamentIds);
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("삭제할 토너먼트를 선택해주세요.");
        }

        for (Long tournamentId : normalizedIds) {
            deleteTournament(tournamentId);
        }

        log.info("Deleted tournaments. tournamentIds={}", normalizedIds);
        return ResponseDto.success(null);
    }

    public ResponseDto<TournamentDetailResponseDto> getPublicTournament(Long tournamentId) {
        try {
            return tournamentRepository.findByIdAndStatusIn(tournamentId, PUBLIC_STATUSES)
                    .map(tournament -> ResponseDto.success(buildDetail(tournament)))
                    .orElseGet(() -> ResponseDto.fail(HttpServletResponse.SC_NOT_FOUND, "토너먼트를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.warn("Failed to get public tournament. tournamentId={}", tournamentId, e);
            return ResponseDto.fail("토너먼트 상세 조회에 실패했습니다.");
        }
    }

    @Transactional
    public TournamentDetailResponseDto assignMatchMap(Long tournamentId, Long matchId, Long mapId) {
        TournamentEntity tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new NoSuchElementException("토너먼트를 찾을 수 없습니다."));
        TournamentMatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NoSuchElementException("경기를 찾을 수 없습니다."));
        TournamentStageEntity stage = stageRepository.findById(match.getStageId())
                .orElseThrow(() -> new NoSuchElementException("토너먼트 스테이지를 찾을 수 없습니다."));

        if (!Objects.equals(stage.getTournamentId(), tournament.getId())) {
            throw new IllegalArgumentException("해당 토너먼트의 경기가 아닙니다.");
        }

        if (TournamentMatchEntity.STATUS_FINISHED.equals(match.getStatus())
                || TournamentMatchEntity.STATUS_CANCELLED.equals(match.getStatus())) {
            throw new IllegalArgumentException("Finished or cancelled match map cannot be changed.");
        }
        if (scoreSubmissionRepository.existsByTournamentIdAndMatchIdAndStatusNot(
                tournamentId,
                matchId,
                TournamentMatchScoreSubmissionEntity.STATUS_REJECTED
        )) {
            throw new IllegalArgumentException("Match map cannot be changed after score submission.");
        }

        if (mapId != null && !mapRepository.existsById(mapId)) {
            throw new IllegalArgumentException("존재하지 않는 맵입니다.");
        }

        match.assignMap(mapId);
        return buildDetail(tournament);
    }

    @Transactional
    public TournamentDetailResponseDto assignRaceSurvivalMatchParticipants(
            Long tournamentId,
            Long matchId,
            Long slot1ParticipantId,
            Long slot2ParticipantId,
            Long actorUserId,
            String actorRole
    ) {
        TournamentEntity tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new NoSuchElementException("토너먼트를 찾을 수 없습니다."));
        TournamentMatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NoSuchElementException("경기를 찾을 수 없습니다."));
        TournamentStageEntity stage = stageRepository.findById(match.getStageId())
                .orElseThrow(() -> new NoSuchElementException("토너먼트 스테이지를 찾을 수 없습니다."));

        if (!Objects.equals(stage.getTournamentId(), tournament.getId())) {
            throw new IllegalArgumentException("해당 토너먼트의 경기가 아닙니다.");
        }
        if (!TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(stage.getStageType())) {
            throw new IllegalArgumentException("종족 최강전 경기만 선수 지정을 변경할 수 있습니다.");
        }
        requireRaceSurvivalParticipantManager(tournament.getId(), actorUserId, actorRole);
        if (TournamentEntity.STATUS_FINISHED.equals(tournament.getStatus())) {
            throw new IllegalArgumentException("종료된 토너먼트는 선수 지정을 변경할 수 없습니다.");
        }
        if (TournamentMatchEntity.STATUS_FINISHED.equals(match.getStatus())
                || TournamentMatchEntity.STATUS_CANCELLED.equals(match.getStatus())
                || match.getWinnerParticipantId() != null) {
            throw new IllegalArgumentException("완료되었거나 취소된 경기는 선수 지정을 변경할 수 없습니다.");
        }

        List<TournamentMatchSlotEntity> slots = matchSlotRepository.findAllByMatchIdOrderBySlotNoAsc(matchId);
        if (slots.stream().anyMatch(slot -> slot.getScore() != null || Integer.valueOf(1).equals(slot.getIsWinner()))) {
            throw new IllegalArgumentException("점수가 입력된 경기는 선수 지정을 변경할 수 없습니다.");
        }
        if (scoreSubmissionRepository.existsByTournamentIdAndMatchIdAndStatusNot(
                tournamentId,
                matchId,
                TournamentMatchScoreSubmissionEntity.STATUS_REJECTED
        )) {
            throw new IllegalArgumentException("승인 대기 또는 승인된 제출 내역이 있는 경기는 선수 지정을 변경할 수 없습니다.");
        }

        RaceSurvivalState state = loadRaceSurvivalState(stage.getId());
        Long fixedWinnerParticipantId = resolvePreviousRaceSurvivalWinner(stage.getId(), match);
        Long effectiveSlot1ParticipantId = slot1ParticipantId;
        if (fixedWinnerParticipantId != null) {
            if (slot1ParticipantId != null && !Objects.equals(slot1ParticipantId, fixedWinnerParticipantId)) {
                throw new IllegalArgumentException("이 경기의 첫 번째 선수는 직전 경기 승자로 고정됩니다.");
            }
            effectiveSlot1ParticipantId = fixedWinnerParticipantId;
        } else if (match.getDisplayOrder() != null && match.getDisplayOrder() > 1) {
            throw new IllegalArgumentException("이전 경기 승자가 확정된 뒤 다음 경기를 지정할 수 있습니다.");
        }

        TournamentParticipantEntity slot1Participant = requireRaceSurvivalParticipant(state, effectiveSlot1ParticipantId, "slot1ParticipantId");
        TournamentParticipantEntity slot2Participant = requireRaceSurvivalParticipant(state, slot2ParticipantId, "slot2ParticipantId");
        validateRaceSurvivalPair(state, slot1Participant, slot2Participant);

        Map<Integer, TournamentMatchSlotEntity> slotsByNo = slots.stream()
                .collect(Collectors.toMap(TournamentMatchSlotEntity::getSlotNo, Function.identity()));
        TournamentMatchSlotEntity slot1 = getOrCreateRaceSurvivalSlot(match.getId(), slotsByNo, 1);
        TournamentMatchSlotEntity slot2 = getOrCreateRaceSurvivalSlot(match.getId(), slotsByNo, 2);
        assignRaceSurvivalSlot(slot1, effectiveSlot1ParticipantId);
        assignRaceSurvivalSlot(slot2, slot2ParticipantId);

        if (effectiveSlot1ParticipantId != null && slot2ParticipantId != null) {
            match.markReady();
        } else {
            match.markPending();
        }

        return buildDetail(tournament);
    }

    private void requireRaceSurvivalParticipantManager(Long tournamentId, Long actorUserId, String actorRole) {
        if (ADMIN_ROLES.contains(actorRole)) {
            return;
        }
        if (actorUserId == null) {
            throw new AccessDeniedException("Authentication is required.");
        }
        if (participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(tournamentId, actorUserId).isPresent()) {
            return;
        }
        throw new AccessDeniedException("Only tournament participants or administrators can assign match players.");
    }

    private RaceSurvivalState loadRaceSurvivalState(Long stageId) {
        List<TournamentGroupEntity> raceGroups = groupRepository.findAllByStageIdOrderByDisplayOrderAsc(stageId)
                .stream()
                .filter(group -> RACE_ORDER.contains(group.getGroupCode()))
                .toList();
        if (raceGroups.size() != RACE_ORDER.size()) {
            throw new IllegalArgumentException("종족 최강전 그룹 정보를 찾을 수 없습니다.");
        }

        Map<Long, String> raceByParticipantId = new HashMap<>();
        Map<Long, TournamentParticipantEntity> participantById = new HashMap<>();
        for (TournamentGroupEntity group : raceGroups) {
            List<TournamentGroupEntryEntity> entries = groupEntryRepository.findAllByGroupIdOrderByGroupSeedNoAsc(group.getId());
            List<Long> participantIds = entries.stream()
                    .map(TournamentGroupEntryEntity::getParticipantId)
                    .toList();
            Map<Long, TournamentParticipantEntity> participants = participantIds.isEmpty()
                    ? Map.of()
                    : participantRepository.findAllById(participantIds)
                    .stream()
                    .collect(Collectors.toMap(TournamentParticipantEntity::getId, Function.identity()));

            for (TournamentGroupEntryEntity entry : entries) {
                TournamentParticipantEntity participant = participants.get(entry.getParticipantId());
                if (participant == null) {
                    continue;
                }
                raceByParticipantId.put(participant.getId(), group.getGroupCode());
                participantById.put(participant.getId(), participant);
            }
        }
        return new RaceSurvivalState(raceByParticipantId, participantById);
    }

    private Long resolvePreviousRaceSurvivalWinner(Long stageId, TournamentMatchEntity match) {
        int displayOrder = match.getDisplayOrder() == null ? 1 : match.getDisplayOrder();
        if (displayOrder <= 1) {
            return null;
        }

        return matchRepository.findAllByStageIdOrderByDisplayOrderAsc(stageId)
                .stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), match.getId()))
                .filter(candidate -> candidate.getDisplayOrder() != null && candidate.getDisplayOrder() < displayOrder)
                .filter(candidate -> TournamentMatchEntity.STATUS_FINISHED.equals(candidate.getStatus()))
                .filter(candidate -> candidate.getWinnerParticipantId() != null)
                .max(Comparator.comparing(TournamentMatchEntity::getDisplayOrder))
                .map(TournamentMatchEntity::getWinnerParticipantId)
                .orElse(null);
    }

    private TournamentParticipantEntity requireRaceSurvivalParticipant(
            RaceSurvivalState state,
            Long participantId,
            String fieldName
    ) {
        if (participantId == null) {
            return null;
        }
        TournamentParticipantEntity participant = state.participantById().get(participantId);
        if (participant == null) {
            throw new IllegalArgumentException(fieldName + " is not a RACE_SURVIVAL participant.");
        }
        if (TournamentParticipantEntity.STATUS_DROPPED.equals(participant.getStatus())) {
            throw new IllegalArgumentException("탈락한 선수는 경기 선수로 지정할 수 없습니다.");
        }
        return participant;
    }

    private void validateRaceSurvivalPair(
            RaceSurvivalState state,
            TournamentParticipantEntity slot1Participant,
            TournamentParticipantEntity slot2Participant
    ) {
        if (slot1Participant == null || slot2Participant == null) {
            return;
        }
        if (Objects.equals(slot1Participant.getId(), slot2Participant.getId())) {
            throw new IllegalArgumentException("같은 선수를 양쪽 슬롯에 지정할 수 없습니다.");
        }
        String slot1Race = state.raceByParticipantId().get(slot1Participant.getId());
        String slot2Race = state.raceByParticipantId().get(slot2Participant.getId());
        if (Objects.equals(slot1Race, slot2Race)) {
            throw new IllegalArgumentException("종족 최강전은 서로 다른 팀 선수끼리만 경기할 수 있습니다.");
        }
    }

    private TournamentMatchSlotEntity getOrCreateRaceSurvivalSlot(
            Long matchId,
            Map<Integer, TournamentMatchSlotEntity> slotsByNo,
            int slotNo
    ) {
        TournamentMatchSlotEntity existing = slotsByNo.get(slotNo);
        if (existing != null) {
            return existing;
        }
        return matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                .matchId(matchId)
                .slotNo(slotNo)
                .placeholderLabel(RACE_SURVIVAL_EMPTY_SLOT_LABEL)
                .isWinner(0)
                .isBye(0)
                .build());
    }

    private void assignRaceSurvivalSlot(TournamentMatchSlotEntity slot, Long participantId) {
        if (participantId == null) {
            slot.clearParticipant(RACE_SURVIVAL_EMPTY_SLOT_LABEL);
            return;
        }
        slot.assignParticipant(participantId);
    }

    private TournamentSummaryResponseDto toSummary(TournamentEntity tournament) {
        List<TournamentStageEntity> stages = stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournament.getId());
        List<Long> stageIds = stages.stream()
                .map(TournamentStageEntity::getId)
                .toList();
        int groupCount = stageIds.isEmpty() ? 0 : Math.toIntExact(groupRepository.countByStageIdIn(stageIds));
        int participantCount = Math.toIntExact(participantRepository.countByTournamentId(tournament.getId()));

        return TournamentSummaryResponseDto.builder()
                .id(tournament.getId())
                .title(tournament.getTitle())
                .bracketType(resolveBracketType(stages))
                .status(tournament.getStatus())
                .groupCount(groupCount)
                .participantCount(participantCount)
                .regDate(tournament.getRegDate())
                .updateDate(tournament.getUpdateDate())
                .build();
    }

    private void deleteTournament(Long tournamentId) {
        tournamentRepository.findByIdForUpdate(tournamentId)
                .orElseThrow(() -> new NoSuchElementException("토너먼트를 찾을 수 없습니다."));

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
        List<Long> matchIds = collectTournamentMatchIds(stageIds, groupIds);

        List<Long> raceProgressSubmissionIds = raceSurvivalProgressSubmissionRepository
                .findAllByTournamentIdOrderByRegDateDescIdDesc(tournamentId)
                .stream()
                .map(submission -> submission.getId())
                .toList();
        if (!raceProgressSubmissionIds.isEmpty()) {
            raceSurvivalProgressSubmissionMatchRepository.deleteBySubmissionIdIn(raceProgressSubmissionIds);
        }
        raceSurvivalProgressSubmissionRepository.deleteByTournamentId(tournamentId);
        scoreSubmissionRepository.deleteByTournamentId(tournamentId);
        if (!matchIds.isEmpty()) {
            routeRepository.deleteByFromMatchIdIn(matchIds);
            routeRepository.deleteByToMatchIdIn(matchIds);
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
        tournamentRepository.deleteById(tournamentId);
    }

    private List<Long> collectTournamentMatchIds(List<Long> stageIds, List<Long> groupIds) {
        Map<Long, TournamentMatchEntity> matchesById = new HashMap<>();
        if (!groupIds.isEmpty()) {
            matchRepository.findAllByGroupIdInOrderByDisplayOrderAsc(groupIds)
                    .forEach(match -> matchesById.putIfAbsent(match.getId(), match));
        }
        if (!stageIds.isEmpty()) {
            matchRepository.findAllByStageIdInOrderByDisplayOrderAsc(stageIds)
                    .forEach(match -> matchesById.putIfAbsent(match.getId(), match));
        }

        return matchesById.keySet().stream()
                .sorted()
                .toList();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }

    private List<Long> normalizeTournamentIds(List<Long> tournamentIds) {
        if (tournamentIds == null) {
            return List.of();
        }

        return tournamentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public TournamentDetailResponseDto buildDetail(TournamentEntity tournament) {
        List<TournamentParticipantEntity> participants = participantRepository
                .findAllByTournamentIdOrderBySeedNoAscIdAsc(tournament.getId());
        Map<Long, UserEntity> usersById = loadUsers(participants);
        List<TournamentParticipantResponseDto> participantDtos = participants.stream()
                .map(participant -> toParticipant(participant, findUserOrNull(participant, usersById)))
                .toList();
        Map<Long, TournamentParticipantResponseDto> participantsById = participantDtos.stream()
                .collect(Collectors.toMap(TournamentParticipantResponseDto::getId, Function.identity()));

        List<TournamentStageEntity> stages = stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournament.getId());
        Map<Long, Integer> stageOrderById = indexStages(stages);
        List<Long> stageIds = stages.stream()
                .map(TournamentStageEntity::getId)
                .toList();
        List<TournamentGroupEntity> groups = stageIds.isEmpty()
                ? List.of()
                : groupRepository.findAllByStageIdInOrderByDisplayOrderAsc(stageIds);
        groups = sortGroups(groups, stageOrderById);
        List<Long> groupIds = groups.stream()
                .map(TournamentGroupEntity::getId)
                .toList();

        Map<Long, List<TournamentGroupEntryEntity>> entriesByGroupId = loadEntriesByGroupId(groupIds);
        Map<Long, List<TournamentMatchEntity>> matchesByGroupId = loadMatchesByGroupId(groupIds);
        Map<Long, List<TournamentMatchSlotEntity>> slotsByMatchId = loadSlotsByMatchId(matchesByGroupId);
        Map<Long, List<TournamentResultSlotEntity>> resultSlotsByGroupId = loadResultSlotsByGroupId(groupIds);
        Map<Long, String> mapNamesById = loadMapNames(matchesByGroupId);

        Map<Long, List<TournamentGroupResponseDto>> groupDtosByStageId = new HashMap<>();
        for (TournamentGroupEntity group : groups) {
            TournamentGroupResponseDto groupDto = toGroup(
                    group,
                    entriesByGroupId.getOrDefault(group.getId(), List.of()),
                    matchesByGroupId.getOrDefault(group.getId(), List.of()),
                    slotsByMatchId,
                    resultSlotsByGroupId.getOrDefault(group.getId(), List.of()),
                    participantsById,
                    mapNamesById
            );
            groupDtosByStageId.computeIfAbsent(group.getStageId(), ignored -> new ArrayList<>()).add(groupDto);
        }

        List<TournamentStageResponseDto> stageDtos = stages.stream()
                .map(stage -> toStage(stage, groupDtosByStageId.getOrDefault(stage.getId(), List.of())))
                .toList();
        List<TournamentGroupResponseDto> publicGroups = chooseFrontendGroups(stages, groupDtosByStageId, groups);

        return TournamentDetailResponseDto.builder()
                .id(tournament.getId())
                .title(tournament.getTitle())
                .bracketType(resolveBracketType(stages))
                .status(tournament.getStatus())
                .groupCount(groups.size())
                .participantCount(participants.size())
                .regDate(tournament.getRegDate())
                .updateDate(tournament.getUpdateDate())
                .participants(participantDtos)
                .stages(stageDtos)
                .groups(publicGroups)
                .build();
    }

    private String resolveBracketType(List<TournamentStageEntity> stages) {
        return stages.stream()
                .map(TournamentStageEntity::getStageType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Map<Long, UserEntity> loadUsers(List<TournamentParticipantEntity> participants) {
        Set<Long> userIds = participants.stream()
                .map(TournamentParticipantEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private UserEntity findUserOrNull(
            TournamentParticipantEntity participant,
            Map<Long, UserEntity> usersById
    ) {
        Long userId = participant.getUserId();
        if (userId == null) {
            return null;
        }
        return usersById.get(userId);
    }

    private Map<Long, Integer> indexStages(List<TournamentStageEntity> stages) {
        Map<Long, Integer> stageOrderById = new HashMap<>();
        for (int index = 0; index < stages.size(); index++) {
            stageOrderById.put(stages.get(index).getId(), index);
        }
        return stageOrderById;
    }

    private List<TournamentGroupEntity> sortGroups(List<TournamentGroupEntity> groups, Map<Long, Integer> stageOrderById) {
        return groups.stream()
                .sorted(Comparator
                        .comparing((TournamentGroupEntity group) -> stageOrderById.getOrDefault(group.getStageId(), Integer.MAX_VALUE))
                        .thenComparing(group -> nullsLast(group.getDisplayOrder()))
                        .thenComparing(TournamentGroupEntity::getId))
                .toList();
    }

    private Map<Long, List<TournamentGroupEntryEntity>> loadEntriesByGroupId(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }

        return groupEntryRepository.findAllByGroupIdInOrderByGroupSeedNoAsc(groupIds).stream()
                .sorted(Comparator
                        .comparing((TournamentGroupEntryEntity entry) -> nullsLast(entry.getGroupSeedNo()))
                        .thenComparing(TournamentGroupEntryEntity::getId))
                .collect(Collectors.groupingBy(TournamentGroupEntryEntity::getGroupId));
    }

    private Map<Long, List<TournamentMatchEntity>> loadMatchesByGroupId(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }

        return matchRepository.findAllByGroupIdInOrderByDisplayOrderAsc(groupIds).stream()
                .sorted(Comparator
                        .comparing((TournamentMatchEntity match) -> nullsLast(match.getDisplayOrder()))
                        .thenComparing(TournamentMatchEntity::getId))
                .collect(Collectors.groupingBy(TournamentMatchEntity::getGroupId));
    }

    private Map<Long, List<TournamentMatchSlotEntity>> loadSlotsByMatchId(Map<Long, List<TournamentMatchEntity>> matchesByGroupId) {
        List<Long> matchIds = matchesByGroupId.values().stream()
                .flatMap(List::stream)
                .map(TournamentMatchEntity::getId)
                .toList();
        if (matchIds.isEmpty()) {
            return Map.of();
        }

        return matchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(matchIds).stream()
                .sorted(Comparator
                        .comparing((TournamentMatchSlotEntity slot) -> nullsLast(slot.getSlotNo()))
                        .thenComparing(TournamentMatchSlotEntity::getId))
                .collect(Collectors.groupingBy(TournamentMatchSlotEntity::getMatchId));
    }

    private Map<Long, String> loadMapNames(Map<Long, List<TournamentMatchEntity>> matchesByGroupId) {
        List<Long> mapIds = matchesByGroupId.values().stream()
                .flatMap(List::stream)
                .map(TournamentMatchEntity::getMapId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (mapIds.isEmpty()) {
            return Map.of();
        }

        return mapRepository.findAllById(mapIds).stream()
                .collect(Collectors.toMap(MapEntity::getId, MapEntity::getMapName));
    }

    private Map<Long, List<TournamentResultSlotEntity>> loadResultSlotsByGroupId(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }

        return resultSlotRepository.findAllByGroupIdInOrderByRankNoAscIdAsc(groupIds).stream()
                .sorted(Comparator
                        .comparing((TournamentResultSlotEntity slot) -> nullsLast(slot.getRankNo()))
                        .thenComparing(TournamentResultSlotEntity::getId))
                .collect(Collectors.groupingBy(TournamentResultSlotEntity::getGroupId));
    }

    private TournamentParticipantResponseDto toParticipant(TournamentParticipantEntity participant, UserEntity user) {
        String displayName = resolveDisplayName(participant, user);
        Integer seedNo = participant.getSeedNo();

        return TournamentParticipantResponseDto.builder()
                .id(participant.getId())
                .userId(participant.getUserId())
                .userLoginId(user == null ? null : user.getUserId())
                .participantName(participant.getParticipantName())
                .displayName(displayName)
                .seedNo(seedNo)
                .seedLabel(seedNo == null ? null : String.valueOf(seedNo))
                .status(participant.getStatus())
                .build();
    }

    private String resolveDisplayName(TournamentParticipantEntity participant, UserEntity user) {
        if (user != null && user.getUserId() != null && !user.getUserId().isBlank()) {
            return user.getUserId();
        }
        return participant.getParticipantName();
    }

    private TournamentGroupResponseDto toGroup(
            TournamentGroupEntity group,
            List<TournamentGroupEntryEntity> entries,
            List<TournamentMatchEntity> matches,
            Map<Long, List<TournamentMatchSlotEntity>> slotsByMatchId,
            List<TournamentResultSlotEntity> resultSlots,
            Map<Long, TournamentParticipantResponseDto> participantsById,
            Map<Long, String> mapNamesById
    ) {
        List<TournamentParticipantResponseDto> groupParticipants = entries.stream()
                .map(TournamentGroupEntryEntity::getParticipantId)
                .map(participantsById::get)
                .filter(Objects::nonNull)
                .toList();

        return TournamentGroupResponseDto.builder()
                .id(group.getId())
                .stageId(group.getStageId())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .displayOrder(group.getDisplayOrder())
                .description(buildGroupDescription(group))
                .participants(groupParticipants)
                .matches(matches.stream()
                        .map(match -> toMatch(match, slotsByMatchId.getOrDefault(match.getId(), List.of()), participantsById, mapNamesById))
                        .toList())
                .resultSlots(resultSlots.stream()
                        .map(resultSlot -> toResultSlot(resultSlot, participantsById))
                        .toList())
                .build();
    }

    private String buildGroupDescription(TournamentGroupEntity group) {
        if ("A".equals(group.getGroupCode()) || "B".equals(group.getGroupCode())) {
            return group.getGroupName() + " dual group bracket";
        }
        return group.getGroupName();
    }

    private TournamentMatchResponseDto toMatch(
            TournamentMatchEntity match,
            List<TournamentMatchSlotEntity> slots,
            Map<Long, TournamentParticipantResponseDto> participantsById,
            Map<Long, String> mapNamesById
    ) {
        return TournamentMatchResponseDto.builder()
                .id(match.getId())
                .stageId(match.getStageId())
                .groupId(match.getGroupId())
                .matchKey(match.getMatchKey())
                .matchRole(match.getMatchRole())
                .roundNo(match.getRoundNo())
                .matchNo(match.getMatchNo())
                .displayName(match.getDisplayName())
                .bestOf(match.getBestOf())
                .status(match.getStatus())
                .winnerParticipantId(match.getWinnerParticipantId())
                .mapId(match.getMapId())
                .mapName(match.getMapId() == null ? null : mapNamesById.get(match.getMapId()))
                .scheduledAt(match.getScheduledAt())
                .layoutCol(match.getLayoutCol())
                .layoutRow(match.getLayoutRow())
                .displayOrder(match.getDisplayOrder())
                .slots(slots.stream()
                        .map(slot -> toMatchSlot(slot, participantsById))
                        .toList())
                .build();
    }

    private TournamentMatchSlotResponseDto toMatchSlot(
            TournamentMatchSlotEntity slot,
            Map<Long, TournamentParticipantResponseDto> participantsById
    ) {
        return TournamentMatchSlotResponseDto.builder()
                .id(slot.getId())
                .slotNo(slot.getSlotNo())
                .participantId(slot.getParticipantId())
                .participant(participantsById.get(slot.getParticipantId()))
                .sourceMatchId(slot.getSourceMatchId())
                .sourceOutcome(slot.getSourceOutcome())
                .placeholderLabel(slot.getPlaceholderLabel())
                .score(slot.getScore())
                .isWinner(Integer.valueOf(1).equals(slot.getIsWinner()))
                .isBye(Integer.valueOf(1).equals(slot.getIsBye()))
                .build();
    }

    private TournamentResultSlotResponseDto toResultSlot(
            TournamentResultSlotEntity resultSlot,
            Map<Long, TournamentParticipantResponseDto> participantsById
    ) {
        return TournamentResultSlotResponseDto.builder()
                .id(resultSlot.getId())
                .stageId(resultSlot.getStageId())
                .groupId(resultSlot.getGroupId())
                .resultKey(resultSlot.getResultKey())
                .resultType(resultSlot.getResultType())
                .rankNo(resultSlot.getRankNo())
                .label(resultSlot.getLabel())
                .participantId(resultSlot.getParticipantId())
                .participant(participantsById.get(resultSlot.getParticipantId()))
                .decidedAt(resultSlot.getDecidedAt())
                .build();
    }

    private TournamentStageResponseDto toStage(TournamentStageEntity stage, List<TournamentGroupResponseDto> groups) {
        return TournamentStageResponseDto.builder()
                .id(stage.getId())
                .stageNo(stage.getStageNo())
                .stageName(stage.getStageName())
                .stageType(stage.getStageType())
                .status(stage.getStatus())
                .displayOrder(stage.getDisplayOrder())
                .groups(groups)
                .build();
    }

    private List<TournamentGroupResponseDto> chooseFrontendGroups(
            List<TournamentStageEntity> stages,
            Map<Long, List<TournamentGroupResponseDto>> groupDtosByStageId,
            List<TournamentGroupEntity> groups
    ) {
        List<TournamentGroupResponseDto> dualGroupDtos = stages.stream()
                .filter(stage -> TournamentStageEntity.TYPE_DUAL_GROUP.equals(stage.getStageType()))
                .flatMap(stage -> groupDtosByStageId.getOrDefault(stage.getId(), List.of()).stream())
                .toList();
        if (!dualGroupDtos.isEmpty()) {
            return dualGroupDtos;
        }

        return groups.stream()
                .map(group -> groupDtosByStageId.getOrDefault(group.getStageId(), List.of()).stream()
                        .filter(groupDto -> Objects.equals(groupDto.getId(), group.getId()))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private int nullsLast(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private record RaceSurvivalState(
            Map<Long, String> raceByParticipantId,
            Map<Long, TournamentParticipantEntity> participantById
    ) {
    }
}
