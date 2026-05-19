package io.github.gyulbbe.tournament.service;

import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionMatchRequestDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionMatchResponseDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionRejectRequestDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionRequestDto;
import io.github.gyulbbe.tournament.dto.RaceSurvivalProgressSubmissionResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.dto.TournamentParticipantResponseDto;
import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionEntity;
import io.github.gyulbbe.tournament.entity.RaceSurvivalProgressSubmissionMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntity;
import io.github.gyulbbe.tournament.entity.TournamentGroupEntryEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentMatchSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentParticipantEntity;
import io.github.gyulbbe.tournament.entity.TournamentResultSlotEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.RaceSurvivalProgressSubmissionMatchRepository;
import io.github.gyulbbe.tournament.repository.RaceSurvivalProgressSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupEntryRepository;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RaceSurvivalProgressSubmissionService {

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_MANAGER",
            "ROLE_MASTER",
            "ROLE_ADMIN",
            "MANAGER",
            "MASTER",
            "ADMIN"
    );
    private static final List<String> RACE_ORDER = List.of("TERRAN", "ZERG", "PROTOSS");
    private static final String MATCHES_GROUP_CODE = "MATCHES";
    private static final String AUTO_REJECT_NOTE = "Another RACE_SURVIVAL progress submission was approved.";
    private static final String REPLACED_NOTE = "Replaced by newer RACE_SURVIVAL progress submission.";

    private final TournamentRepository tournamentRepository;
    private final TournamentStageRepository stageRepository;
    private final TournamentGroupRepository groupRepository;
    private final TournamentGroupEntryRepository groupEntryRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository matchRepository;
    private final TournamentMatchSlotRepository matchSlotRepository;
    private final TournamentResultSlotRepository resultSlotRepository;
    private final TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;
    private final RaceSurvivalProgressSubmissionRepository submissionRepository;
    private final RaceSurvivalProgressSubmissionMatchRepository submissionMatchRepository;
    private final UserRepository userRepository;
    private final MapRepository mapRepository;
    private final TournamentService tournamentService;

    @Transactional
    public RaceSurvivalProgressSubmissionResponseDto submitProgress(
            Long tournamentId,
            RaceSurvivalProgressSubmissionRequestDto request,
            Long actorUserId,
            String actorRole
    ) {
        requireAuthenticated(actorUserId);
        RaceSurvivalContext context = loadContext(tournamentId);
        requireSubmitter(context.tournament().getId(), actorUserId, actorRole);
        validateTournamentAcceptsSubmission(context.tournament());

        List<ValidatedMatch> validatedMatches = validateProgressMatches(
                context,
                normalizeRequestMatches(request)
        );

        LocalDateTime replacedAt = LocalDateTime.now();
        submissionRepository.findAllByTournamentIdAndSubmittedByUserIdAndStatus(
                        tournamentId,
                        actorUserId,
                        RaceSurvivalProgressSubmissionEntity.STATUS_PENDING
                )
                .forEach(submission -> submission.reject(actorUserId, replacedAt, REPLACED_NOTE));

        RaceSurvivalProgressSubmissionEntity submission = submissionRepository.save(
                RaceSurvivalProgressSubmissionEntity.builder()
                        .tournamentId(tournamentId)
                        .submittedByUserId(actorUserId)
                        .status(RaceSurvivalProgressSubmissionEntity.STATUS_PENDING)
                        .build()
        );

        List<RaceSurvivalProgressSubmissionMatchEntity> savedMatches = new ArrayList<>();
        for (ValidatedMatch validatedMatch : validatedMatches) {
            savedMatches.add(submissionMatchRepository.save(RaceSurvivalProgressSubmissionMatchEntity.builder()
                    .submissionId(submission.getId())
                    .matchOrder(validatedMatch.matchOrder())
                    .mapId(validatedMatch.mapId())
                    .slot1ParticipantId(validatedMatch.slot1ParticipantId())
                    .slot2ParticipantId(validatedMatch.slot2ParticipantId())
                    .slot1Score(validatedMatch.slot1Score())
                    .slot2Score(validatedMatch.slot2Score())
                    .build()));
        }

        return toResponse(submission, savedMatches, context);
    }

    @Transactional(readOnly = true)
    public List<RaceSurvivalProgressSubmissionResponseDto> listSubmissions(
            Long tournamentId,
            Long actorUserId,
            String actorRole
    ) {
        requireAuthenticated(actorUserId);
        RaceSurvivalContext context = loadContext(tournamentId);
        boolean admin = isAdmin(actorRole);
        if (!admin && !isTournamentParticipant(tournamentId, actorUserId)) {
            throw forbidden("Only tournament participants or administrators can view RACE_SURVIVAL progress submissions.");
        }

        List<RaceSurvivalProgressSubmissionEntity> submissions = admin
                ? submissionRepository.findAllByTournamentIdOrderByRegDateDescIdDesc(tournamentId)
                : submissionRepository.findAllByTournamentIdAndSubmittedByUserIdOrderByRegDateDescIdDesc(tournamentId, actorUserId);
        if (submissions.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RaceSurvivalProgressSubmissionMatchEntity>> matchesBySubmissionId =
                loadSubmissionMatches(submissions);
        return submissions.stream()
                .map(submission -> toResponse(
                        submission,
                        matchesBySubmissionId.getOrDefault(submission.getId(), List.of()),
                        context
                ))
                .toList();
    }

    @Transactional
    public TournamentDetailResponseDto approveSubmission(
            Long tournamentId,
            Long submissionId,
            Long adminUserId,
            String actorRole
    ) {
        requireAdmin(actorRole);
        RaceSurvivalContext context = loadContext(tournamentId);
        RaceSurvivalProgressSubmissionEntity submission = findPendingSubmission(tournamentId, submissionId);
        List<RaceSurvivalProgressSubmissionMatchEntity> storedMatches =
                submissionMatchRepository.findAllBySubmissionIdOrderByMatchOrderAsc(submission.getId());
        List<ValidatedMatch> validatedMatches = validateProgressMatches(context, toRequestMatches(storedMatches));

        assertOfficialProgressNotStarted(context);
        applyOfficialProgress(context, validatedMatches);

        LocalDateTime reviewedAt = LocalDateTime.now();
        submission.approve(adminUserId, reviewedAt);
        submissionRepository.findAllByTournamentIdAndStatus(
                        tournamentId,
                        RaceSurvivalProgressSubmissionEntity.STATUS_PENDING
                )
                .stream()
                .filter(other -> !Objects.equals(other.getId(), submission.getId()))
                .forEach(other -> other.reject(adminUserId, reviewedAt, AUTO_REJECT_NOTE));

        return tournamentService.buildDetail(context.tournament());
    }

    @Transactional
    public RaceSurvivalProgressSubmissionResponseDto rejectSubmission(
            Long tournamentId,
            Long submissionId,
            RaceSurvivalProgressSubmissionRejectRequestDto request,
            Long adminUserId,
            String actorRole
    ) {
        requireAdmin(actorRole);
        RaceSurvivalContext context = loadContext(tournamentId);
        RaceSurvivalProgressSubmissionEntity submission = findPendingSubmission(tournamentId, submissionId);
        submission.reject(adminUserId, LocalDateTime.now(), request == null ? null : request.getAdminNote());
        List<RaceSurvivalProgressSubmissionMatchEntity> matches =
                submissionMatchRepository.findAllBySubmissionIdOrderByMatchOrderAsc(submission.getId());

        return toResponse(submission, matches, context);
    }

    private void validateTournamentAcceptsSubmission(TournamentEntity tournament) {
        if (TournamentEntity.STATUS_FINISHED.equals(tournament.getStatus())) {
            throw invalid("Finished tournament cannot accept RACE_SURVIVAL progress submissions.");
        }
    }

    private List<RaceSurvivalProgressSubmissionMatchRequestDto> normalizeRequestMatches(
            RaceSurvivalProgressSubmissionRequestDto request
    ) {
        if (request == null || request.getMatches() == null || request.getMatches().isEmpty()) {
            throw invalid("RACE_SURVIVAL progress submission requires at least one match.");
        }

        List<RaceSurvivalProgressSubmissionMatchRequestDto> normalized = new ArrayList<>();
        Set<Integer> orders = new HashSet<>();
        for (int index = 0; index < request.getMatches().size(); index++) {
            RaceSurvivalProgressSubmissionMatchRequestDto match = request.getMatches().get(index);
            if (match == null) {
                throw invalid("RACE_SURVIVAL progress match cannot be null.");
            }
            Integer order = match.getMatchOrder() == null ? index + 1 : match.getMatchOrder();
            if (order == null || order <= 0 || !orders.add(order)) {
                throw invalid("RACE_SURVIVAL progress match order must be positive and unique.");
            }
            match.setMatchOrder(order);
            normalized.add(match);
        }

        return normalized.stream()
                .sorted(Comparator.comparing(RaceSurvivalProgressSubmissionMatchRequestDto::getMatchOrder))
                .toList();
    }

    private List<ValidatedMatch> validateProgressMatches(
            RaceSurvivalContext context,
            List<RaceSurvivalProgressSubmissionMatchRequestDto> matches
    ) {
        validateMaps(matches);

        LinkedHashMap<String, Set<Long>> aliveByRace = new LinkedHashMap<>();
        RACE_ORDER.forEach(race -> aliveByRace.put(race, new LinkedHashSet<>()));
        context.raceByParticipantId().forEach((participantId, race) -> aliveByRace.get(race).add(participantId));
        if (aliveByRace.values().stream().anyMatch(Set::isEmpty)) {
            throw invalid("RACE_SURVIVAL requires at least one participant in each race team.");
        }

        Long previousWinnerParticipantId = null;
        List<ValidatedMatch> validatedMatches = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            RaceSurvivalProgressSubmissionMatchRequestDto match = matches.get(index);
            Long slot1ParticipantId = requireParticipant(context, match.getSlot1ParticipantId(), "slot1ParticipantId");
            Long slot2ParticipantId = requireParticipant(context, match.getSlot2ParticipantId(), "slot2ParticipantId");
            if (Objects.equals(slot1ParticipantId, slot2ParticipantId)) {
                throw invalid("The same participant cannot play both slots.");
            }

            String slot1Race = context.raceByParticipantId().get(slot1ParticipantId);
            String slot2Race = context.raceByParticipantId().get(slot2ParticipantId);
            if (Objects.equals(slot1Race, slot2Race)) {
                throw invalid("RACE_SURVIVAL match must use participants from different race teams.");
            }
            if (!aliveByRace.get(slot1Race).contains(slot1ParticipantId)
                    || !aliveByRace.get(slot2Race).contains(slot2ParticipantId)) {
                throw invalid("Dropped participant cannot appear again in RACE_SURVIVAL progress.");
            }
            if (index > 0 && !Objects.equals(previousWinnerParticipantId, slot1ParticipantId)
                    && !Objects.equals(previousWinnerParticipantId, slot2ParticipantId)) {
                throw invalid("Previous match winner must appear in the next RACE_SURVIVAL match.");
            }

            ScoreDecision decision = decideScore(match.getSlot1Score(), match.getSlot2Score());
            Long winnerParticipantId = decision.winnerSlotNo() == 1 ? slot1ParticipantId : slot2ParticipantId;
            Long loserParticipantId = decision.winnerSlotNo() == 1 ? slot2ParticipantId : slot1ParticipantId;
            String loserRace = context.raceByParticipantId().get(loserParticipantId);
            aliveByRace.get(loserRace).remove(loserParticipantId);
            previousWinnerParticipantId = winnerParticipantId;

            Set<String> aliveRaces = aliveRaces(aliveByRace);
            boolean lastRow = index == matches.size() - 1;
            if (aliveRaces.size() <= 1 && !lastRow) {
                throw invalid("RACE_SURVIVAL progress has extra matches after a champion was decided.");
            }

            validatedMatches.add(new ValidatedMatch(
                    match.getMatchOrder(),
                    match.getMapId(),
                    slot1ParticipantId,
                    slot2ParticipantId,
                    match.getSlot1Score(),
                    match.getSlot2Score(),
                    winnerParticipantId,
                    loserParticipantId
            ));
        }

        if (aliveRaces(aliveByRace).size() > 1) {
            throw invalid("RACE_SURVIVAL progress is incomplete. Continue until only one race team remains.");
        }

        return validatedMatches;
    }

    private void validateMaps(List<RaceSurvivalProgressSubmissionMatchRequestDto> matches) {
        List<Long> mapIds = matches.stream()
                .map(RaceSurvivalProgressSubmissionMatchRequestDto::getMapId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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
            throw invalid("Unknown mapId in RACE_SURVIVAL progress submission.");
        }
    }

    private Long requireParticipant(RaceSurvivalContext context, Long participantId, String fieldName) {
        if (participantId == null) {
            throw invalid(fieldName + " is required.");
        }
        if (!context.raceByParticipantId().containsKey(participantId)) {
            throw invalid(fieldName + " is not a RACE_SURVIVAL participant.");
        }
        return participantId;
    }

    private ScoreDecision decideScore(Integer slot1Score, Integer slot2Score) {
        if (slot1Score == null || slot2Score == null) {
            throw invalid("RACE_SURVIVAL score is required.");
        }
        if (slot1Score == 1 && slot2Score == 0) {
            return new ScoreDecision(1);
        }
        if (slot1Score == 0 && slot2Score == 1) {
            return new ScoreDecision(2);
        }
        throw invalid("RACE_SURVIVAL score must be 1:0 or 0:1.");
    }

    private Set<String> aliveRaces(Map<String, Set<Long>> aliveByRace) {
        return aliveByRace.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void assertOfficialProgressNotStarted(RaceSurvivalContext context) {
        if (TournamentEntity.STATUS_FINISHED.equals(context.tournament().getStatus())) {
            throw invalid("Already finished RACE_SURVIVAL tournament cannot approve a progress submission.");
        }
        List<TournamentMatchEntity> officialMatches = matchRepository.findAllByStageIdOrderByDisplayOrderAsc(context.stage().getId());
        if (officialMatches.stream().anyMatch(match ->
                TournamentMatchEntity.STATUS_FINISHED.equals(match.getStatus())
                        || match.getWinnerParticipantId() != null
        )) {
            throw invalid("RACE_SURVIVAL official progress has already started.");
        }
        List<Long> officialMatchIds = officialMatches.stream()
                .map(TournamentMatchEntity::getId)
                .toList();
        if (!officialMatchIds.isEmpty()) {
            boolean hasDecidedSlot = matchSlotRepository.findAllByMatchIdInOrderBySlotNoAsc(officialMatchIds)
                    .stream()
                    .anyMatch(slot -> slot.getScore() != null || Integer.valueOf(1).equals(slot.getIsWinner()));
            if (hasDecidedSlot) {
                throw invalid("RACE_SURVIVAL official progress has already started.");
            }
        }
        if (context.participantsById().values().stream()
                .anyMatch(participant -> TournamentParticipantEntity.STATUS_DROPPED.equals(participant.getStatus()))) {
            throw invalid("RACE_SURVIVAL official progress has already started.");
        }
        if (resultSlotRepository.findAllByStageIdOrderByRankNoAscIdAsc(context.stage().getId()).stream()
                .anyMatch(resultSlot -> resultSlot.getParticipantId() != null)) {
            throw invalid("RACE_SURVIVAL official progress has already started.");
        }
    }

    private void applyOfficialProgress(RaceSurvivalContext context, List<ValidatedMatch> validatedMatches) {
        TournamentGroupEntity matchesGroup = context.groupsByCode().get(MATCHES_GROUP_CODE);
        if (matchesGroup == null) {
            throw notFound("RACE_SURVIVAL matches group not found.");
        }

        List<TournamentMatchEntity> existingMatches = matchRepository.findAllByGroupIdOrderByDisplayOrderAsc(matchesGroup.getId());
        List<Long> existingMatchIds = existingMatches.stream()
                .map(TournamentMatchEntity::getId)
                .toList();
        scoreSubmissionRepository.deleteByTournamentId(context.tournament().getId());
        if (!existingMatchIds.isEmpty()) {
            matchSlotRepository.deleteByMatchIdIn(existingMatchIds);
            matchRepository.deleteByGroupIdIn(List.of(matchesGroup.getId()));
        }

        context.participantsById().values()
                .forEach(participant -> participant.updateStatus(TournamentParticipantEntity.STATUS_READY));

        for (ValidatedMatch validatedMatch : validatedMatches) {
            TournamentMatchEntity match = matchRepository.save(TournamentMatchEntity.builder()
                    .stageId(context.stage().getId())
                    .groupId(matchesGroup.getId())
                    .matchKey("M" + validatedMatch.matchOrder())
                    .matchRole(TournamentMatchEntity.ROLE_ROUND)
                    .roundNo(validatedMatch.matchOrder())
                    .matchNo(validatedMatch.matchOrder())
                    .displayName("Match " + validatedMatch.matchOrder())
                    .bestOf(1)
                    .status(TournamentMatchEntity.STATUS_FINISHED)
                    .winnerParticipantId(validatedMatch.winnerParticipantId())
                    .mapId(validatedMatch.mapId())
                    .layoutCol(1)
                    .layoutRow(validatedMatch.matchOrder())
                    .displayOrder(validatedMatch.matchOrder())
                    .build());
            saveOfficialSlot(match.getId(), 1, validatedMatch.slot1ParticipantId(), validatedMatch.slot1Score(), validatedMatch.winnerParticipantId());
            saveOfficialSlot(match.getId(), 2, validatedMatch.slot2ParticipantId(), validatedMatch.slot2Score(), validatedMatch.winnerParticipantId());
            context.participantsById().get(validatedMatch.loserParticipantId()).updateStatus(TournamentParticipantEntity.STATUS_DROPPED);
        }

        Long championParticipantId = validatedMatches.get(validatedMatches.size() - 1).winnerParticipantId();
        resultSlotRepository.findByStageIdAndResultKey(context.stage().getId(), "CHAMPION")
                .ifPresent(resultSlot -> resultSlot.decide(championParticipantId, LocalDateTime.now()));
        context.tournament().finish();
    }

    private void saveOfficialSlot(
            Long matchId,
            int slotNo,
            Long participantId,
            Integer score,
            Long winnerParticipantId
    ) {
        matchSlotRepository.save(TournamentMatchSlotEntity.builder()
                .matchId(matchId)
                .slotNo(slotNo)
                .participantId(participantId)
                .score(score)
                .isWinner(Objects.equals(participantId, winnerParticipantId) ? 1 : 0)
                .isBye(0)
                .build());
    }

    private RaceSurvivalProgressSubmissionEntity findPendingSubmission(Long tournamentId, Long submissionId) {
        RaceSurvivalProgressSubmissionEntity submission = submissionRepository.findByIdAndTournamentId(submissionId, tournamentId)
                .orElseThrow(() -> notFound("RACE_SURVIVAL progress submission not found."));
        if (!RaceSurvivalProgressSubmissionEntity.STATUS_PENDING.equals(submission.getStatus())) {
            throw invalid("Only PENDING RACE_SURVIVAL progress submissions can be reviewed.");
        }
        return submission;
    }

    private RaceSurvivalContext loadContext(Long tournamentId) {
        TournamentEntity tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> notFound("Tournament not found."));
        TournamentStageEntity stage = stageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournamentId)
                .stream()
                .filter(candidate -> TournamentStageEntity.TYPE_RACE_SURVIVAL.equals(candidate.getStageType()))
                .findFirst()
                .orElseThrow(() -> invalid("Tournament is not RACE_SURVIVAL."));
        List<TournamentGroupEntity> groups = groupRepository.findAllByStageIdOrderByDisplayOrderAsc(stage.getId());
        Map<String, TournamentGroupEntity> groupsByCode = groups.stream()
                .collect(Collectors.toMap(TournamentGroupEntity::getGroupCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (String race : RACE_ORDER) {
            if (!groupsByCode.containsKey(race)) {
                throw invalid("RACE_SURVIVAL requires TERRAN, ZERG, and PROTOSS groups.");
            }
        }

        List<TournamentGroupEntity> raceGroups = RACE_ORDER.stream()
                .map(groupsByCode::get)
                .toList();
        List<TournamentGroupEntryEntity> entries = groupEntryRepository
                .findAllByGroupIdInOrderByGroupSeedNoAsc(raceGroups.stream().map(TournamentGroupEntity::getId).toList());
        Map<Long, TournamentParticipantEntity> participantsById = participantRepository.findAllById(
                        entries.stream().map(TournamentGroupEntryEntity::getParticipantId).distinct().toList()
                )
                .stream()
                .collect(Collectors.toMap(TournamentParticipantEntity::getId, Function.identity()));
        Map<Long, String> raceByParticipantId = new LinkedHashMap<>();
        Map<Long, String> groupCodeByGroupId = raceGroups.stream()
                .collect(Collectors.toMap(TournamentGroupEntity::getId, TournamentGroupEntity::getGroupCode));
        for (TournamentGroupEntryEntity entry : entries) {
            TournamentParticipantEntity participant = participantsById.get(entry.getParticipantId());
            String race = groupCodeByGroupId.get(entry.getGroupId());
            if (participant != null && race != null) {
                raceByParticipantId.put(participant.getId(), race);
            }
        }

        return new RaceSurvivalContext(tournament, stage, groupsByCode, participantsById, raceByParticipantId);
    }

    private Map<Long, List<RaceSurvivalProgressSubmissionMatchEntity>> loadSubmissionMatches(
            List<RaceSurvivalProgressSubmissionEntity> submissions
    ) {
        List<Long> submissionIds = submissions.stream()
                .map(RaceSurvivalProgressSubmissionEntity::getId)
                .toList();
        return submissionMatchRepository.findAllBySubmissionIdInOrderBySubmissionIdAscMatchOrderAsc(submissionIds)
                .stream()
                .collect(Collectors.groupingBy(RaceSurvivalProgressSubmissionMatchEntity::getSubmissionId));
    }

    private List<RaceSurvivalProgressSubmissionMatchRequestDto> toRequestMatches(
            List<RaceSurvivalProgressSubmissionMatchEntity> matches
    ) {
        RaceSurvivalProgressSubmissionRequestDto request = new RaceSurvivalProgressSubmissionRequestDto();
        request.setMatches(matches.stream()
                .map(match -> {
                    RaceSurvivalProgressSubmissionMatchRequestDto requestMatch = new RaceSurvivalProgressSubmissionMatchRequestDto();
                    requestMatch.setMatchOrder(match.getMatchOrder());
                    requestMatch.setMapId(match.getMapId());
                    requestMatch.setSlot1ParticipantId(match.getSlot1ParticipantId());
                    requestMatch.setSlot2ParticipantId(match.getSlot2ParticipantId());
                    requestMatch.setSlot1Score(match.getSlot1Score());
                    requestMatch.setSlot2Score(match.getSlot2Score());
                    return requestMatch;
                })
                .toList());
        return normalizeRequestMatches(request);
    }

    private RaceSurvivalProgressSubmissionResponseDto toResponse(
            RaceSurvivalProgressSubmissionEntity submission,
            List<RaceSurvivalProgressSubmissionMatchEntity> matches,
            RaceSurvivalContext context
    ) {
        Map<Long, UserEntity> usersById = loadUsers(context, List.of(submission));
        Map<Long, String> mapNamesById = loadMapNames(matches);

        return RaceSurvivalProgressSubmissionResponseDto.builder()
                .id(submission.getId())
                .tournamentId(submission.getTournamentId())
                .submittedByUserId(submission.getSubmittedByUserId())
                .submitterLoginId(resolveLoginId(usersById.get(submission.getSubmittedByUserId())))
                .status(submission.getStatus())
                .reviewedByUserId(submission.getReviewedByUserId())
                .reviewerLoginId(resolveLoginId(usersById.get(submission.getReviewedByUserId())))
                .adminNote(submission.getAdminNote())
                .regDate(submission.getRegDate())
                .reviewedAt(submission.getReviewedAt())
                .matches(matches.stream()
                        .sorted(Comparator.comparing(RaceSurvivalProgressSubmissionMatchEntity::getMatchOrder))
                        .map(match -> toMatchResponse(match, context, mapNamesById, usersById))
                        .toList())
                .build();
    }

    private RaceSurvivalProgressSubmissionMatchResponseDto toMatchResponse(
            RaceSurvivalProgressSubmissionMatchEntity match,
            RaceSurvivalContext context,
            Map<Long, String> mapNamesById,
            Map<Long, UserEntity> usersById
    ) {
        Long winnerParticipantId = match.getSlot1Score() > match.getSlot2Score()
                ? match.getSlot1ParticipantId()
                : match.getSlot2ParticipantId();

        return RaceSurvivalProgressSubmissionMatchResponseDto.builder()
                .id(match.getId())
                .matchOrder(match.getMatchOrder())
                .mapId(match.getMapId())
                .mapName(match.getMapId() == null ? null : mapNamesById.get(match.getMapId()))
                .slot1ParticipantId(match.getSlot1ParticipantId())
                .slot1Participant(toParticipant(context.participantsById().get(match.getSlot1ParticipantId()), usersById))
                .slot1Race(context.raceByParticipantId().get(match.getSlot1ParticipantId()))
                .slot2ParticipantId(match.getSlot2ParticipantId())
                .slot2Participant(toParticipant(context.participantsById().get(match.getSlot2ParticipantId()), usersById))
                .slot2Race(context.raceByParticipantId().get(match.getSlot2ParticipantId()))
                .slot1Score(match.getSlot1Score())
                .slot2Score(match.getSlot2Score())
                .winnerParticipantId(winnerParticipantId)
                .winnerParticipant(toParticipant(context.participantsById().get(winnerParticipantId), usersById))
                .build();
    }

    private Map<Long, UserEntity> loadUsers(
            RaceSurvivalContext context,
            List<RaceSurvivalProgressSubmissionEntity> submissions
    ) {
        Set<Long> userIds = new HashSet<>();
        submissions.forEach(submission -> {
            if (submission.getSubmittedByUserId() != null) {
                userIds.add(submission.getSubmittedByUserId());
            }
            if (submission.getReviewedByUserId() != null) {
                userIds.add(submission.getReviewedByUserId());
            }
        });
        context.participantsById().values().stream()
                .map(TournamentParticipantEntity::getUserId)
                .filter(Objects::nonNull)
                .forEach(userIds::add);
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private Map<Long, String> loadMapNames(List<RaceSurvivalProgressSubmissionMatchEntity> matches) {
        List<Long> mapIds = matches.stream()
                .map(RaceSurvivalProgressSubmissionMatchEntity::getMapId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (mapIds.isEmpty()) {
            return Map.of();
        }
        return mapRepository.findAllById(mapIds).stream()
                .collect(Collectors.toMap(MapEntity::getId, MapEntity::getMapName));
    }

    private TournamentParticipantResponseDto toParticipant(
            TournamentParticipantEntity participant,
            Map<Long, UserEntity> usersById
    ) {
        if (participant == null) {
            return null;
        }
        UserEntity user = participant.getUserId() == null
                ? null
                : usersById.get(participant.getUserId());
        String loginId = resolveLoginId(user);
        String displayName = loginId == null || loginId.isBlank()
                ? participant.getParticipantName()
                : loginId;

        return TournamentParticipantResponseDto.builder()
                .id(participant.getId())
                .userId(participant.getUserId())
                .userLoginId(loginId)
                .participantName(participant.getParticipantName())
                .displayName(displayName)
                .seedNo(participant.getSeedNo())
                .seedLabel(participant.getSeedNo() == null ? null : String.valueOf(participant.getSeedNo()))
                .status(participant.getStatus())
                .build();
    }

    private String resolveLoginId(UserEntity user) {
        return user == null ? null : user.getUserId();
    }

    private void requireSubmitter(Long tournamentId, Long actorUserId, String actorRole) {
        if (isAdmin(actorRole) || isTournamentParticipant(tournamentId, actorUserId)) {
            return;
        }
        throw forbidden("Only tournament participants or administrators can submit RACE_SURVIVAL progress.");
    }

    private boolean isTournamentParticipant(Long tournamentId, Long actorUserId) {
        return actorUserId != null
                && participantRepository.findFirstByTournamentIdAndUserIdOrderBySeedNoAscIdAsc(tournamentId, actorUserId).isPresent();
    }

    private boolean isAdmin(String actorRole) {
        return ADMIN_ROLES.contains(actorRole);
    }

    private void requireAdmin(String actorRole) {
        if (!isAdmin(actorRole)) {
            throw forbidden("Administrator role is required.");
        }
    }

    private void requireAuthenticated(Long actorUserId) {
        if (actorUserId == null) {
            throw forbidden("Authentication is required.");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private NoSuchElementException notFound(String message) {
        return new NoSuchElementException(message);
    }

    private AccessDeniedException forbidden(String message) {
        return new AccessDeniedException(message);
    }

    private record RaceSurvivalContext(
            TournamentEntity tournament,
            TournamentStageEntity stage,
            Map<String, TournamentGroupEntity> groupsByCode,
            Map<Long, TournamentParticipantEntity> participantsById,
            Map<Long, String> raceByParticipantId
    ) {
    }

    private record ValidatedMatch(
            Integer matchOrder,
            Long mapId,
            Long slot1ParticipantId,
            Long slot2ParticipantId,
            Integer slot1Score,
            Integer slot2Score,
            Long winnerParticipantId,
            Long loserParticipantId
    ) {
    }

    private record ScoreDecision(Integer winnerSlotNo) {
    }
}
