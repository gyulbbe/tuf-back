package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.common.dto.ResponseDto;
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
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final List<String> PUBLIC_STATUSES = List.of(
            TournamentEntity.STATUS_LIVE,
            TournamentEntity.STATUS_FINISHED
    );

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
    private final UserRepository userRepository;

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

        Map<Long, List<TournamentGroupResponseDto>> groupDtosByStageId = new HashMap<>();
        for (TournamentGroupEntity group : groups) {
            TournamentGroupResponseDto groupDto = toGroup(
                    group,
                    entriesByGroupId.getOrDefault(group.getId(), List.of()),
                    matchesByGroupId.getOrDefault(group.getId(), List.of()),
                    slotsByMatchId,
                    resultSlotsByGroupId.getOrDefault(group.getId(), List.of()),
                    participantsById
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
            Map<Long, TournamentParticipantResponseDto> participantsById
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
                        .map(match -> toMatch(match, slotsByMatchId.getOrDefault(match.getId(), List.of()), participantsById))
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
            Map<Long, TournamentParticipantResponseDto> participantsById
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
}
