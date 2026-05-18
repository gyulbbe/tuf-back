package io.github.gyulbbe.league.service;

import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.league.dto.AdminLeagueDeleteResponseDto;
import io.github.gyulbbe.league.dto.AdminLeaguePageResponseDto;
import io.github.gyulbbe.league.dto.AdminLeagueRaceSurvivalTeamRequestDto;
import io.github.gyulbbe.league.dto.AdminLeagueRaceSurvivalTeamResponseDto;
import io.github.gyulbbe.league.dto.AdminLeagueRequestDto;
import io.github.gyulbbe.league.dto.AdminLeagueResponseDto;
import io.github.gyulbbe.league.dto.AdminLeagueSummaryResponseDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeaguePlayerRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeaguePlayerResponseDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueResponseDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueTournamentRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueResponseDto;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueQueryRepository;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamMemberRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
import io.github.gyulbbe.tournament.dto.TournamentCreateGroupRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentCreateSlotRequestDto;
import io.github.gyulbbe.tournament.dto.TournamentDetailResponseDto;
import io.github.gyulbbe.tournament.entity.TournamentMatchEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentMatchRepository;
import io.github.gyulbbe.tournament.repository.TournamentMatchScoreSubmissionRepository;
import io.github.gyulbbe.tournament.repository.TournamentResultSlotRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.tournament.service.TournamentCreationService;
import io.github.gyulbbe.tournament.service.TournamentService;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminLeagueService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final String ACTIVE_USER_STATUS = "ACTIVE";
    private static final String PARTICIPATION_STATUS_ACTIVE = "ACTIVE";
    private static final String LINKED_FILTER_LINKED = "LINKED";
    private static final String LINKED_FILTER_UNLINKED = "UNLINKED";
    private static final List<String> RACE_ORDER = List.of("TERRAN", "ZERG", "PROTOSS");

    private final LeagueRepository leagueRepository;
    private final LeagueQueryRepository leagueQueryRepository;
    private final LeagueParticipationRepository participationRepository;
    private final ProleagueTeamRepository proleagueTeamRepository;
    private final ProleagueTeamMemberRepository proleagueTeamMemberRepository;
    private final DraftSessionRepository draftSessionRepository;
    private final DraftTeamRepository draftTeamRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final DraftOrderRepository draftOrderRepository;
    private final DraftPickRepository draftPickRepository;
    private final UserRepository userRepository;
    private final AdminProleagueService adminProleagueService;
    private final AdminPersonalLeagueService adminPersonalLeagueService;
    private final TournamentCreationService tournamentCreationService;
    private final TournamentService tournamentService;
    private final TournamentStageRepository tournamentStageRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;
    private final TournamentResultSlotRepository tournamentResultSlotRepository;

    public AdminLeagueResponseDto createLeague(AdminLeagueRequestDto request, Long ownerUserId) {
        String leagueType = normalizeLeagueType(request == null ? null : request.getLeagueType());
        String status = normalizeUnifiedStatus(request == null ? null : request.getStatus());
        if (LeagueEntity.TYPE_PROLEAGUE.equals(leagueType)) {
            AdminProleagueCreateRequestDto proleagueRequest = toProleagueRequest(request, status);
            return fromProleague(adminProleagueService.createProleague(proleagueRequest, ownerUserId));
        }
        if (LeagueEntity.TYPE_PERSONAL.equals(leagueType)) {
            AdminPersonalLeagueCreateRequestDto personalRequest = toPersonalLeagueRequest(request, status);
            return fromPersonalLeague(adminPersonalLeagueService.createPersonalLeague(personalRequest, ownerUserId));
        }
        if (LeagueEntity.TYPE_ULTIMATE_BATTLE.equals(leagueType)) {
            return createSpecialLeague(request, ownerUserId, leagueType, status, resolveUltimatePlayers(request));
        }
        if (LeagueEntity.TYPE_RACE_SURVIVAL.equals(leagueType)) {
            return createRaceSurvivalLeague(request, ownerUserId, status);
        }
        throw new IllegalArgumentException("Unsupported leagueType.");
    }

    @Transactional(readOnly = true)
    public AdminLeagueResponseDto getLeague(Long leagueId) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NoSuchElementException("League not found."));
        if (LeagueEntity.TYPE_PROLEAGUE.equals(league.getLeagueType())) {
            return fromProleague(adminProleagueService.getProleague(leagueId));
        }
        if (LeagueEntity.TYPE_PERSONAL.equals(league.getLeagueType())) {
            return fromPersonalLeague(adminPersonalLeagueService.getPersonalLeague(leagueId));
        }
        return fromSpecialLeague(league);
    }

    @Transactional(readOnly = true)
    public AdminLeaguePageResponseDto listLeagues(
            String leagueType,
            int page,
            int size,
            String keyword,
            String status,
            String linked
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedType = normalizeOptionalLeagueType(leagueType);
        String normalizedStatus = normalizeOptionalListStatus(status);
        String normalizedLinked = normalizeLinkedFilter(linked);
        Page<Long> ids = leagueQueryRepository.findAdminLeagueIds(
                normalizedType,
                trimToNull(keyword),
                normalizedStatus,
                normalizedLinked,
                normalizedPage,
                normalizedSize
        );

        AdminLeaguePageResponseDto response = new AdminLeaguePageResponseDto();
        response.setItems(ids.getContent().stream()
                .map(this::toSummaryResponse)
                .toList());
        response.setPage(normalizedPage);
        response.setSize(normalizedSize);
        response.setTotalElements(ids.getTotalElements());
        response.setTotalPages(ids.getTotalPages());
        response.setHasNext(ids.hasNext());
        response.setHasPrevious(ids.hasPrevious());
        return response;
    }

    public AdminLeagueSummaryResponseDto finishLeague(Long leagueId) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NoSuchElementException("League not found."));
        if (!LeagueEntity.STATUS_FINISHED.equals(league.getStatus())) {
            league.finish(
                    league.getChampionTeamId(),
                    league.getRunnerUpTeamId(),
                    league.getEndDate() == null ? LocalDate.now() : league.getEndDate()
            );
        }
        return toSummaryResponse(leagueId);
    }

    public AdminLeagueDeleteResponseDto deleteLeague(Long leagueId) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NoSuchElementException("League not found."));
        DeleteGuard deleteGuard = resolveDeleteGuard(league);
        if (!deleteGuard.canDelete()) {
            throw new IllegalStateException(deleteGuard.reason());
        }

        league.clearResultTeams();
        leagueRepository.saveAndFlush(league);
        deleteLinkedDrafts(league);
        deleteLinkedTournament(league);
        participationRepository.deleteByLeagueId(leagueId);
        proleagueTeamMemberRepository.deleteByLeagueId(leagueId);
        draftTeamRepository.unlinkProleagueTeamsByLeagueId(leagueId);
        draftSessionRepository.unlinkProleagueByProleagueId(leagueId);
        proleagueTeamRepository.deleteByLeagueId(leagueId);
        leagueRepository.delete(league);
        return new AdminLeagueDeleteResponseDto(1);
    }

    public AdminLeagueResponseDto updateLeague(Long leagueId, AdminLeagueRequestDto request, Long ownerUserId) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NoSuchElementException("League not found."));
        String leagueType = normalizeLeagueType(request == null ? null : request.getLeagueType());
        String status = normalizeUnifiedStatus(request == null ? null : request.getStatus());
        if (!Objects.equals(league.getLeagueType(), leagueType)) {
            throw new IllegalArgumentException("leagueType cannot be changed.");
        }
        if (LeagueEntity.TYPE_PROLEAGUE.equals(leagueType)) {
            return fromProleague(adminProleagueService.updateProleague(leagueId, toProleagueRequest(request, status), ownerUserId));
        }
        if (LeagueEntity.TYPE_PERSONAL.equals(leagueType)) {
            return fromPersonalLeague(adminPersonalLeagueService.updatePersonalLeague(leagueId, toPersonalLeagueRequest(request, status), ownerUserId));
        }
        if (LeagueEntity.STATUS_FINISHED.equals(league.getStatus())) {
            throw new IllegalStateException("Finished leagues cannot be updated.");
        }
        if (league.getTournamentId() != null && !Boolean.TRUE.equals(request.getCreateTournament())) {
            throw new IllegalStateException("Linked tournaments cannot be removed from a league.");
        }
        if (league.getTournamentId() != null && hasTournamentProgress(league.getTournamentId())) {
            throw new IllegalStateException("Leagues with progressed tournaments cannot be updated.");
        }

        league.updateBasic(
                requireLeagueName(request),
                trimToNull(request.getSeasonName()),
                trimToNull(request.getDescription()),
                status,
                request.getStartDate(),
                request.getEndDate()
        );
        participationRepository.deleteByLeagueId(leagueId);

        if (LeagueEntity.TYPE_ULTIMATE_BATTLE.equals(leagueType)) {
            List<ResolvedPlayer> players = resolveUltimatePlayers(request);
            createParticipations(leagueId, players, null);
            replaceOrCreateLinkedTournament(league, request, ownerUserId, toUltimateTournamentRequest(league, players, request));
            return fromSpecialLeague(league);
        }

        List<ResolvedRaceTeam> raceTeams = resolveRaceTeams(request);
        createRaceParticipations(leagueId, raceTeams);
        replaceOrCreateLinkedTournament(league, request, ownerUserId, toRaceSurvivalTournamentRequest(league, raceTeams));
        return fromSpecialLeague(league);
    }

    private AdminLeagueResponseDto createSpecialLeague(
            AdminLeagueRequestDto request,
            Long ownerUserId,
            String leagueType,
            String status,
            List<ResolvedPlayer> players
    ) {
        LeagueEntity league = saveBasicLeague(request, leagueType, status);
        createParticipations(league.getId(), players, null);
        if (Boolean.TRUE.equals(request.getCreateTournament())) {
            TournamentDetailResponseDto tournament = tournamentCreationService.createTournament(
                    toUltimateTournamentRequest(league, players, request),
                    ownerUserId
            );
            league.linkTournament(tournament.getId());
        }
        return fromSpecialLeague(league);
    }

    private AdminLeagueResponseDto createRaceSurvivalLeague(AdminLeagueRequestDto request, Long ownerUserId, String status) {
        List<ResolvedRaceTeam> raceTeams = resolveRaceTeams(request);
        LeagueEntity league = saveBasicLeague(request, LeagueEntity.TYPE_RACE_SURVIVAL, status);
        createRaceParticipations(league.getId(), raceTeams);
        if (Boolean.TRUE.equals(request.getCreateTournament())) {
            TournamentDetailResponseDto tournament = tournamentCreationService.createTournament(
                    toRaceSurvivalTournamentRequest(league, raceTeams),
                    ownerUserId
            );
            league.linkTournament(tournament.getId());
        }
        return fromSpecialLeague(league);
    }

    private LeagueEntity saveBasicLeague(AdminLeagueRequestDto request, String leagueType, String status) {
        return leagueRepository.saveAndFlush(LeagueEntity.builder()
                .leagueName(requireLeagueName(request))
                .seasonName(trimToNull(request.getSeasonName()))
                .description(trimToNull(request.getDescription()))
                .status(status)
                .leagueType(leagueType)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build());
    }

    private void replaceOrCreateLinkedTournament(
            LeagueEntity league,
            AdminLeagueRequestDto request,
            Long ownerUserId,
            TournamentCreateRequestDto tournamentRequest
    ) {
        if (!Boolean.TRUE.equals(request.getCreateTournament())) {
            return;
        }
        if (league.getTournamentId() == null) {
            TournamentDetailResponseDto created = tournamentCreationService.createTournament(tournamentRequest, ownerUserId);
            league.linkTournament(created.getId());
            return;
        }
        tournamentCreationService.replaceTournament(league.getTournamentId(), tournamentRequest, ownerUserId);
    }

    private AdminProleagueCreateRequestDto toProleagueRequest(AdminLeagueRequestDto request, String status) {
        AdminProleagueCreateRequestDto dto = new AdminProleagueCreateRequestDto();
        dto.setLeagueName(requireLeagueName(request));
        dto.setSeasonName(trimToNull(request.getSeasonName()));
        dto.setDescription(trimToNull(request.getDescription()));
        dto.setStatus(status);
        dto.setLeagueType(LeagueEntity.TYPE_PROLEAGUE);
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setCreateDraft(Boolean.TRUE.equals(request.getCreateDraft()));
        dto.setTeams(request.getTeams());
        dto.setDraft(request.getDraft());
        return dto;
    }

    private AdminPersonalLeagueCreateRequestDto toPersonalLeagueRequest(AdminLeagueRequestDto request, String status) {
        AdminPersonalLeagueCreateRequestDto dto = new AdminPersonalLeagueCreateRequestDto();
        dto.setLeagueName(requireLeagueName(request));
        dto.setSeasonName(trimToNull(request.getSeasonName()));
        dto.setDescription(trimToNull(request.getDescription()));
        dto.setStatus(status);
        dto.setLeagueType(LeagueEntity.TYPE_PERSONAL);
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setCreateTournament(Boolean.TRUE.equals(request.getCreateTournament()));
        dto.setPlayers(request.getPlayers());
        dto.setTournament(request.getTournament());
        return dto;
    }

    private TournamentCreateRequestDto toUltimateTournamentRequest(
            LeagueEntity league,
            List<ResolvedPlayer> players,
            AdminLeagueRequestDto request
    ) {
        TournamentCreateRequestDto dto = new TournamentCreateRequestDto();
        dto.setTitle(league.getLeagueName() + " Tournament");
        dto.setBracketType(TournamentStageEntity.TYPE_ULTIMATE_BATTLE);
        dto.setBestOf(normalizeTotalGames(request.getTotalGames()));
        dto.setPublishNow(true);
        dto.setGroups(List.of(toGroup("MAIN", "Ultimate Battle", players.stream()
                .map(ResolvedPlayer::user)
                .toList())));
        return dto;
    }

    private TournamentCreateRequestDto toRaceSurvivalTournamentRequest(LeagueEntity league, List<ResolvedRaceTeam> raceTeams) {
        TournamentCreateRequestDto dto = new TournamentCreateRequestDto();
        dto.setTitle(league.getLeagueName() + " Tournament");
        dto.setBracketType(TournamentStageEntity.TYPE_RACE_SURVIVAL);
        dto.setBestOf(1);
        dto.setPublishNow(true);
        dto.setGroups(raceTeams.stream()
                .map(team -> toGroup(team.race(), team.race(), team.players().stream().map(ResolvedPlayer::user).toList()))
                .toList());
        return dto;
    }

    private TournamentCreateGroupRequestDto toGroup(String groupCode, String groupName, List<UserEntity> users) {
        TournamentCreateGroupRequestDto group = new TournamentCreateGroupRequestDto();
        group.setGroupCode(groupCode);
        group.setGroupName(groupName);
        List<TournamentCreateSlotRequestDto> slots = new ArrayList<>();
        for (int index = 0; index < users.size(); index++) {
            TournamentCreateSlotRequestDto slot = new TournamentCreateSlotRequestDto();
            slot.setSlotNo(index + 1);
            slot.setUserId(users.get(index).getId());
            slots.add(slot);
        }
        group.setSlots(slots);
        return group;
    }

    private void createParticipations(Long leagueId, List<ResolvedPlayer> players, String raceOverride) {
        for (ResolvedPlayer player : players) {
            participationRepository.save(LeagueParticipationEntity.builder()
                    .leagueId(leagueId)
                    .userId(player.user().getId())
                    .race(raceOverride == null ? defaultRace(player.user().getRace()) : raceOverride)
                    .status(PARTICIPATION_STATUS_ACTIVE)
                    .build());
        }
    }

    private void createRaceParticipations(Long leagueId, List<ResolvedRaceTeam> raceTeams) {
        for (ResolvedRaceTeam raceTeam : raceTeams) {
            createParticipations(leagueId, raceTeam.players(), raceTeam.race());
        }
    }

    private List<ResolvedPlayer> resolveUltimatePlayers(AdminLeagueRequestDto request) {
        List<ResolvedPlayer> players = resolvePlayers(request == null ? null : request.getPlayers());
        if (players.size() != 2) {
            throw new IllegalArgumentException("ULTIMATE_BATTLE requires exactly two players.");
        }
        normalizeTotalGames(request.getTotalGames());
        return players;
    }

    private List<ResolvedRaceTeam> resolveRaceTeams(AdminLeagueRequestDto request) {
        List<AdminLeagueRaceSurvivalTeamRequestDto> teams = request == null ? null : request.getRaceTeams();
        if (teams == null || teams.size() != RACE_ORDER.size()) {
            throw new IllegalArgumentException("RACE_SURVIVAL requires TERRAN, ZERG, and PROTOSS teams.");
        }
        Map<String, List<AdminPersonalLeaguePlayerRequestDto>> playersByRace = new LinkedHashMap<>();
        for (AdminLeagueRaceSurvivalTeamRequestDto team : teams) {
            String race = team == null ? null : normalizeRace(team.getRace());
            if (!RACE_ORDER.contains(race)) {
                throw new IllegalArgumentException("RACE_SURVIVAL race must be TERRAN, ZERG, or PROTOSS.");
            }
            if (playersByRace.put(race, team.getPlayers()) != null) {
                throw new IllegalArgumentException("Duplicate RACE_SURVIVAL race is not allowed.");
            }
        }
        List<ResolvedRaceTeam> resolvedTeams = new ArrayList<>();
        Set<String> loginIds = new LinkedHashSet<>();
        for (String race : RACE_ORDER) {
            List<AdminPersonalLeaguePlayerRequestDto> players = playersByRace.get(race);
            if (players == null || players.isEmpty()) {
                throw new IllegalArgumentException("Each RACE_SURVIVAL team requires at least one player.");
            }
            List<ResolvedPlayer> resolvedPlayers = resolvePlayers(players);
            for (ResolvedPlayer player : resolvedPlayers) {
                if (!loginIds.add(player.user().getUserId().toUpperCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Duplicate player userId is not allowed.");
                }
            }
            resolvedTeams.add(new ResolvedRaceTeam(race, resolvedPlayers));
        }
        return resolvedTeams;
    }

    private List<ResolvedPlayer> resolvePlayers(List<AdminPersonalLeaguePlayerRequestDto> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Players are required.");
        }
        Set<String> loginIds = new LinkedHashSet<>();
        List<ResolvedPlayer> players = new ArrayList<>();
        for (AdminPersonalLeaguePlayerRequestDto request : requests) {
            String loginId = requireText(request == null ? null : request.getUserId(), "Player userId is required.");
            if (!loginIds.add(loginId.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate player userId is not allowed.");
            }
            UserEntity user = userRepository.findByUserIdIgnoreCaseAndStatus(loginId, ACTIVE_USER_STATUS);
            if (user == null) {
                throw new IllegalArgumentException("ACTIVE player not found. userId=" + loginId);
            }
            players.add(new ResolvedPlayer(user));
        }
        return players;
    }

    private AdminLeagueResponseDto fromProleague(AdminProleagueResponseDto source) {
        AdminLeagueResponseDto dto = new AdminLeagueResponseDto();
        copyCommon(dto, source.getId(), source.getLeagueName(), source.getSeasonName(), source.getDescription(),
                source.getStatus(), source.getLeagueType(), source.getStartDate(), source.getEndDate(),
                source.getRegDate(), source.getUpdateDate());
        dto.setDraftSessionId(source.getDraftSessionId());
        dto.setTeams(source.getTeams());
        return dto;
    }

    private AdminLeagueResponseDto fromPersonalLeague(AdminPersonalLeagueResponseDto source) {
        AdminLeagueResponseDto dto = new AdminLeagueResponseDto();
        copyCommon(dto, source.getId(), source.getLeagueName(), source.getSeasonName(), source.getDescription(),
                source.getStatus(), source.getLeagueType(), source.getStartDate(), source.getEndDate(),
                source.getRegDate(), source.getUpdateDate());
        dto.setTournamentId(source.getTournamentId());
        dto.setTournamentBracketType(source.getTournamentBracketType());
        dto.setTournamentBestOf(source.getTournamentBestOf());
        dto.setCanEditTournament(source.getCanEditTournament());
        dto.setPlayers(source.getPlayers());
        return dto;
    }

    private AdminLeagueResponseDto fromSpecialLeague(LeagueEntity league) {
        AdminLeagueResponseDto dto = new AdminLeagueResponseDto();
        copyCommon(dto, league.getId(), league.getLeagueName(), league.getSeasonName(), league.getDescription(),
                league.getStatus(), league.getLeagueType(), league.getStartDate(), league.getEndDate(),
                league.getRegDate(), league.getUpdateDate());
        dto.setTournamentId(league.getTournamentId());
        dto.setTournamentBracketType(resolveTournamentBracketType(league.getTournamentId()));
        dto.setTournamentBestOf(resolveTournamentBestOf(league.getTournamentId()));
        dto.setCanEditTournament(league.getTournamentId() == null || !hasTournamentProgress(league.getTournamentId()));

        List<AdminPersonalLeaguePlayerResponseDto> players = toPlayerResponses(league.getId());
        if (LeagueEntity.TYPE_RACE_SURVIVAL.equals(league.getLeagueType())) {
            dto.setRaceTeams(toRaceTeamResponses(players));
        } else {
            dto.setPlayers(players);
        }
        return dto;
    }

    private void copyCommon(
            AdminLeagueResponseDto dto,
            Long id,
            String leagueName,
            String seasonName,
            String description,
            String status,
            String leagueType,
            LocalDate startDate,
            LocalDate endDate,
            java.time.LocalDateTime regDate,
            java.time.LocalDateTime updateDate
    ) {
        dto.setId(id);
        dto.setLeagueName(leagueName);
        dto.setSeasonName(seasonName);
        dto.setDescription(description);
        dto.setStatus(LeagueEntity.STATUS_READY.equals(status) ? LeagueEntity.STATUS_LIVE : status);
        dto.setLeagueType(leagueType);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setRegDate(regDate);
        dto.setUpdateDate(updateDate);
    }

    private AdminLeagueSummaryResponseDto toSummaryResponse(Long leagueId) {
        LeagueEntity league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NoSuchElementException("League not found."));
        DeleteGuard deleteGuard = resolveDeleteGuard(league);

        AdminLeagueSummaryResponseDto dto = new AdminLeagueSummaryResponseDto();
        dto.setId(league.getId());
        dto.setLeagueName(league.getLeagueName());
        dto.setSeasonName(league.getSeasonName());
        dto.setStatus(LeagueEntity.STATUS_READY.equals(league.getStatus()) ? LeagueEntity.STATUS_LIVE : league.getStatus());
        dto.setLeagueType(league.getLeagueType());
        dto.setStartDate(league.getStartDate());
        dto.setEndDate(league.getEndDate());
        dto.setDraftSessionId(league.getDraftSessionId());
        dto.setTournamentId(league.getTournamentId());
        dto.setTeamCount(resolveTeamCount(league));
        dto.setParticipantCount(participationRepository.countByLeagueId(league.getId()));
        dto.setLinkedType(resolveLinkedType(league));
        dto.setLinkedLabel(resolveLinkedLabel(league));
        dto.setCanDelete(deleteGuard.canDelete());
        dto.setDeleteBlockedReason(deleteGuard.reason());
        dto.setUpdateDate(league.getUpdateDate());
        return dto;
    }

    private Long resolveTeamCount(LeagueEntity league) {
        if (LeagueEntity.TYPE_PROLEAGUE.equals(league.getLeagueType())) {
            return proleagueTeamRepository.countByLeagueId(league.getId());
        }
        if (LeagueEntity.TYPE_RACE_SURVIVAL.equals(league.getLeagueType())) {
            return (long) RACE_ORDER.size();
        }
        return null;
    }

    private String resolveLinkedType(LeagueEntity league) {
        if (LeagueEntity.TYPE_PROLEAGUE.equals(league.getLeagueType()) && hasLinkedDraft(league)) {
            return "DRAFT";
        }
        if (league.getTournamentId() != null) {
            return "TOURNAMENT";
        }
        return null;
    }

    private String resolveLinkedLabel(LeagueEntity league) {
        if (LeagueEntity.TYPE_PROLEAGUE.equals(league.getLeagueType()) && hasLinkedDraft(league)) {
            long linkedDraftCount = countLinkedDrafts(league);
            return linkedDraftCount > 1 ? "드래프트 " + linkedDraftCount + "개" : "드래프트";
        }
        if (league.getTournamentId() != null) {
            String bracketType = resolveTournamentBracketType(league.getTournamentId());
            return bracketType == null ? "토너먼트" : bracketType;
        }
        return null;
    }

    private List<AdminPersonalLeaguePlayerResponseDto> toPlayerResponses(Long leagueId) {
        List<LeagueParticipationEntity> participations = participationRepository.findAllByLeagueIdOrderByIdAsc(leagueId);
        Map<Long, UserEntity> usersById = userRepository.findAllById(participations.stream()
                        .map(LeagueParticipationEntity::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return participations.stream()
                .map(participation -> {
                    AdminPersonalLeaguePlayerResponseDto dto = new AdminPersonalLeaguePlayerResponseDto();
                    UserEntity user = usersById.get(participation.getUserId());
                    dto.setUserId(user == null ? null : user.getUserId());
                    dto.setRace(participation.getRace());
                    dto.setStatus(participation.getStatus());
                    return dto;
                })
                .toList();
    }

    private List<AdminLeagueRaceSurvivalTeamResponseDto> toRaceTeamResponses(List<AdminPersonalLeaguePlayerResponseDto> players) {
        Map<String, List<AdminPersonalLeaguePlayerResponseDto>> playersByRace = players.stream()
                .collect(Collectors.groupingBy(
                        player -> normalizeRace(player.getRace()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<AdminLeagueRaceSurvivalTeamResponseDto> teams = new ArrayList<>();
        for (String race : RACE_ORDER) {
            AdminLeagueRaceSurvivalTeamResponseDto team = new AdminLeagueRaceSurvivalTeamResponseDto();
            team.setRace(race);
            team.setPlayers(playersByRace.getOrDefault(race, List.of()));
            teams.add(team);
        }
        return teams;
    }

    private DeleteGuard resolveDeleteGuard(LeagueEntity league) {
        if (league.getTournamentId() != null && hasTournamentProgress(league.getTournamentId())) {
            return new DeleteGuard(false, "진행 데이터가 있는 토너먼트 연동 리그입니다.");
        }
        if (hasDraftProgress(league)) {
            return new DeleteGuard(false, "픽 기록이 있는 드래프트 연동 리그입니다.");
        }
        return new DeleteGuard(true, null);
    }

    private boolean hasLinkedDraft(LeagueEntity league) {
        return league.getDraftSessionId() != null || draftSessionRepository.countByProleagueId(league.getId()) > 0;
    }

    private long countLinkedDrafts(LeagueEntity league) {
        return linkedDraftSessionIds(league).size();
    }

    private boolean hasDraftProgress(LeagueEntity league) {
        return linkedDraftSessionIds(league).stream()
                .anyMatch(draftSessionId -> draftPickRepository.countByDraftSessionId(draftSessionId) > 0);
    }

    private List<Long> linkedDraftSessionIds(LeagueEntity league) {
        LinkedHashSet<Long> sessionIds = new LinkedHashSet<>();
        if (league.getDraftSessionId() != null) {
            sessionIds.add(league.getDraftSessionId());
        }
        draftSessionRepository.findAllByProleagueId(league.getId())
                .stream()
                .map(DraftSessionEntity::getId)
                .filter(Objects::nonNull)
                .forEach(sessionIds::add);
        return new ArrayList<>(sessionIds);
    }

    private void deleteLinkedDrafts(LeagueEntity league) {
        List<Long> draftSessionIds = linkedDraftSessionIds(league);
        if (draftSessionIds.isEmpty()) {
            return;
        }
        league.unlinkDraftSession();
        leagueRepository.saveAndFlush(league);
        proleagueTeamRepository.unlinkDraftTeamsByLeagueId(league.getId());
        draftTeamRepository.unlinkProleagueTeamsByLeagueId(league.getId());
        draftSessionRepository.unlinkProleagueByProleagueId(league.getId());

        for (Long draftSessionId : draftSessionIds) {
            draftSessionRepository.findById(draftSessionId)
                    .ifPresent(draftSession -> {
                        draftSession.clearCurrentDraftTeam();
                        draftSession.linkProleague(null);
                        draftSessionRepository.saveAndFlush(draftSession);
                    });
            draftPickRepository.deleteByDraftSessionId(draftSessionId);
            draftOrderRepository.deleteByDraftSessionId(draftSessionId);
            draftCandidateRepository.deleteByDraftSessionId(draftSessionId);
            draftTeamRepository.deleteByDraftSessionId(draftSessionId);
            draftSessionRepository.deleteById(draftSessionId);
        }
    }

    private void deleteLinkedTournament(LeagueEntity league) {
        Long tournamentId = league.getTournamentId();
        if (tournamentId == null) {
            return;
        }
        league.unlinkTournament();
        leagueRepository.saveAndFlush(league);
        tournamentService.deleteTournaments(List.of(tournamentId));
    }

    private boolean hasTournamentProgress(Long tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        if (scoreSubmissionRepository.countByTournamentId(tournamentId) > 0) {
            return true;
        }
        List<Long> stageIds = tournamentStageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournamentId)
                .stream()
                .map(TournamentStageEntity::getId)
                .toList();
        if (stageIds.isEmpty()) {
            return false;
        }
        return tournamentMatchRepository.countNonByeDecidedByStageIdIn(stageIds, TournamentMatchEntity.STATUS_FINISHED) > 0
                || tournamentResultSlotRepository.countDecidedByStageIdIn(stageIds) > 0;
    }

    private String resolveTournamentBracketType(Long tournamentId) {
        if (tournamentId == null) {
            return null;
        }
        return tournamentStageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournamentId)
                .stream()
                .findFirst()
                .map(TournamentStageEntity::getStageType)
                .orElse(null);
    }

    private Integer resolveTournamentBestOf(Long tournamentId) {
        if (tournamentId == null) {
            return null;
        }
        List<Long> stageIds = tournamentStageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournamentId)
                .stream()
                .map(TournamentStageEntity::getId)
                .toList();
        if (stageIds.isEmpty()) {
            return null;
        }
        return tournamentMatchRepository.findAllByStageIdInOrderByDisplayOrderAsc(stageIds)
                .stream()
                .findFirst()
                .map(TournamentMatchEntity::getBestOf)
                .orElse(null);
    }

    private String normalizeLeagueType(String leagueType) {
        String normalized = requireText(leagueType, "leagueType is required.").toUpperCase(Locale.ROOT);
        if (Set.of(
                LeagueEntity.TYPE_PROLEAGUE,
                LeagueEntity.TYPE_PERSONAL,
                LeagueEntity.TYPE_ULTIMATE_BATTLE,
                LeagueEntity.TYPE_RACE_SURVIVAL
        ).contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported leagueType.");
    }

    private String normalizeOptionalLeagueType(String leagueType) {
        if (leagueType == null || leagueType.isBlank()) {
            return null;
        }
        return normalizeLeagueType(leagueType);
    }

    private String normalizeUnifiedStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? LeagueEntity.STATUS_LIVE
                : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(LeagueEntity.STATUS_LIVE, LeagueEntity.STATUS_FINISHED).contains(normalized)) {
            throw new IllegalArgumentException("status must be LIVE or FINISHED.");
        }
        return normalized;
    }

    private String normalizeOptionalListStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        return normalizeUnifiedStatus(status);
    }

    private String normalizeLinkedFilter(String linked) {
        if (linked == null || linked.isBlank() || "ALL".equalsIgnoreCase(linked.trim())) {
            return null;
        }
        String normalized = linked.trim().toUpperCase(Locale.ROOT);
        if (Set.of(LINKED_FILTER_LINKED, LINKED_FILTER_UNLINKED).contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("linked must be ALL, LINKED, or UNLINKED.");
    }

    private int normalizePage(int page) {
        return Math.max(page, DEFAULT_PAGE);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private int normalizeTotalGames(Integer totalGames) {
        int normalized = totalGames == null ? 9 : totalGames;
        if (normalized < 1 || normalized % 2 == 0) {
            throw new IllegalArgumentException("totalGames must be a positive odd number.");
        }
        return normalized;
    }

    private String normalizeRace(String race) {
        return requireText(race, "race is required.").toUpperCase(Locale.ROOT);
    }

    private String requireLeagueName(AdminLeagueRequestDto request) {
        String leagueName = requireText(request == null ? null : request.getLeagueName(), "leagueName is required.");
        if (leagueName.length() > 50) {
            throw new IllegalArgumentException("leagueName must be 50 characters or less.");
        }
        return leagueName;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String defaultRace(String race) {
        return race == null || race.isBlank() ? "RANDOM" : race;
    }

    private record ResolvedPlayer(UserEntity user) {
    }

    private record ResolvedRaceTeam(String race, List<ResolvedPlayer> players) {
    }

    private record DeleteGuard(boolean canDelete, String reason) {
    }
}
