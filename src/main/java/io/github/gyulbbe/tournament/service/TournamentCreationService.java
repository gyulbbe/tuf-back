package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.tournament.dto.TournamentCreateGroupRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateSlotRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentRouteEntity;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private static final List<String> RACE_SURVIVAL_GROUP_CODES = List.of(RACE_TERRAN, RACE_ZERG, RACE_PROTOSS);

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentGroupRepository groupRepository;
    private final TournamentGroupEntryRepository groupEntryRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentRouteRepository routeRepository;
    private final TournamentResultSlotRepository resultSlotRepository;
    private final UserRepository userRepository;
    private final TournamentBracketProgressionService bracketProgressionService;
    private final TournamentService tournamentService;

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

        return new NormalizedTournament(
                title,
                bracketType,
                bestOf,
                groups
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
        return new NormalizedGroup(groupIndex, groupCode, groupName, slots);
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
        List<List<TournamentMatchEntity>> rounds = createSingleMatches(stage, group, normalized.bestOf(), bracketSize);
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
            int bracketSize
    ) {
        List<List<TournamentMatchEntity>> rounds = new ArrayList<>();
        int matchesInRound = bracketSize / 2;
        int roundNo = 1;
        int displayOrder = 1;
        while (matchesInRound >= 1) {
            List<TournamentMatchEntity> roundMatches = new ArrayList<>();
            boolean finalRound = matchesInRound == 1;
            for (int matchNo = 1; matchNo <= matchesInRound; matchNo++) {
                TournamentMatchEntity match = matchRepository.save(TournamentMatchEntity.builder()
                        .stageId(stage.getId())
                        .groupId(group.getId())
                        .matchKey(finalRound ? "FINAL" : "R" + roundNo + "M" + matchNo)
                        .matchRole(finalRound ? TournamentMatchEntity.ROLE_FINAL : TournamentMatchEntity.ROLE_ROUND)
                        .roundNo(roundNo)
                        .matchNo(matchNo)
                        .displayName(finalRound ? "Final" : "Round " + roundNo + " Match " + matchNo)
                        .bestOf(bestOf)
                        .status(roundNo == 1 ? TournamentMatchEntity.STATUS_READY : TournamentMatchEntity.STATUS_PENDING)
                        .layoutCol(roundNo)
                        .layoutRow(matchNo)
                        .displayOrder(displayOrder++)
                        .build());
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
            displayOrder = createDualGroupBracket(stage, group, normalizedGroup, normalized.bestOf(), participantsBySlot, displayOrder);
        }
    }

    private int createDualGroupBracket(
            TournamentStageEntity stage,
            TournamentGroupEntity group,
            NormalizedGroup normalizedGroup,
            int bestOf,
            Map<SlotKey, TournamentParticipantEntity> participantsBySlot,
            int displayOrder
    ) {
        String code = normalizedGroup.groupCode();
        List<TournamentParticipantEntity> seats = dualSeats(normalizedGroup, participantsBySlot);
        TournamentParticipantEntity seat1 = seats.get(0);
        TournamentParticipantEntity seat2 = seats.get(1);
        TournamentParticipantEntity seat3 = seats.get(2);
        TournamentParticipantEntity seat4 = seats.get(3);

        TournamentMatchEntity openingOne = saveMatch(stage, group, code + "1", TournamentMatchEntity.ROLE_OPENING, 1, 1, code + "1", bestOf, initialStatus(seat1, seat2), 1, 1, displayOrder++);
        TournamentMatchEntity openingTwo = saveMatch(stage, group, code + "2", TournamentMatchEntity.ROLE_OPENING, 1, 2, code + "2", bestOf, initialStatus(seat3, seat4), 1, 2, displayOrder++);
        TournamentMatchEntity winners = saveMatch(stage, group, code + "W", TournamentMatchEntity.ROLE_WINNERS, 2, 1, code + " Winners", bestOf, TournamentMatchEntity.STATUS_PENDING, 2, 1, displayOrder++);
        TournamentMatchEntity losers = saveMatch(stage, group, code + "L", TournamentMatchEntity.ROLE_LOSERS, 2, 2, code + " Losers", bestOf, TournamentMatchEntity.STATUS_PENDING, 2, 2, displayOrder++);
        TournamentMatchEntity decider = saveMatch(stage, group, code + "F", TournamentMatchEntity.ROLE_DECIDER, 3, 1, code + " Decider", bestOf, TournamentMatchEntity.STATUS_PENDING, 3, 1, displayOrder++);

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

        saveMatchSlotRoute(openingOne.getId(), TournamentRouteEntity.OUTCOME_WINNER, winners.getId(), 1);
        saveMatchSlotRoute(openingTwo.getId(), TournamentRouteEntity.OUTCOME_WINNER, winners.getId(), 2);
        if (hasTwoActualParticipants(seat1, seat2)) {
            saveMatchSlotRoute(openingOne.getId(), TournamentRouteEntity.OUTCOME_LOSER, losers.getId(), 1);
        }
        if (hasTwoActualParticipants(seat3, seat4)) {
            saveMatchSlotRoute(openingTwo.getId(), TournamentRouteEntity.OUTCOME_LOSER, losers.getId(), 2);
        }
        saveResultSlotRoute(winners.getId(), TournamentRouteEntity.OUTCOME_WINNER, first.getId());
        saveMatchSlotRoute(winners.getId(), TournamentRouteEntity.OUTCOME_LOSER, decider.getId(), 1);
        saveMatchSlotRoute(losers.getId(), TournamentRouteEntity.OUTCOME_WINNER, decider.getId(), 2);
        saveEliminatedRoute(losers.getId(), TournamentRouteEntity.OUTCOME_LOSER);
        saveResultSlotRoute(decider.getId(), TournamentRouteEntity.OUTCOME_WINNER, second.getId());
        saveEliminatedRoute(decider.getId(), TournamentRouteEntity.OUTCOME_LOSER);

        return displayOrder;
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
        TournamentMatchEntity match = saveMatch(
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
                1
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
                .layoutCol(layoutCol)
                .layoutRow(layoutRow)
                .displayOrder(displayOrder)
                .build());
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
            List<NormalizedGroup> groups
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
