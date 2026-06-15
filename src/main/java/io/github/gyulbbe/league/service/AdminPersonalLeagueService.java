package io.github.gyulbbe.league.service;

import io.github.gyulbbe.league.dto.AdminPersonalLeagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeaguePlayerRequestDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeaguePlayerResponseDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueResponseDto;
import io.github.gyulbbe.league.dto.AdminPersonalLeagueTournamentRequestDto;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueRepository;
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
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
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
public class AdminPersonalLeagueService {

    private static final int DEFAULT_BEST_OF = 3;
    private static final int DUAL_GROUP_SIZE = 4;
    private static final String ACTIVE_USER_STATUS = "ACTIVE";
    private static final String MAIN_GROUP_CODE = "MAIN";
    private static final String MAIN_GROUP_NAME = "Main Bracket";
    private static final Set<String> BRACKET_TYPES = Set.of(
            TournamentStageEntity.TYPE_SINGLE_ELIMINATION,
            TournamentStageEntity.TYPE_DUAL_GROUP
    );
    private static final Set<String> LEAGUE_STATUSES = Set.of(
            LeagueEntity.STATUS_READY,
            LeagueEntity.STATUS_LIVE,
            LeagueEntity.STATUS_FINISHED
    );

    private final LeagueRepository leagueRepository;
    private final LeagueParticipationRepository leagueParticipationRepository;
    private final UserRepository userRepository;
    private final TournamentCreationService tournamentCreationService;
    private final TournamentStageRepository tournamentStageRepository;
    private final TournamentMatchRepository tournamentMatchRepository;
    private final TournamentMatchScoreSubmissionRepository scoreSubmissionRepository;
    private final TournamentResultSlotRepository tournamentResultSlotRepository;

    public AdminPersonalLeagueResponseDto createPersonalLeague(AdminPersonalLeagueCreateRequestDto request, Long ownerUserId) {
        BasicLeagueValues basic = normalizeBasic(request);
        List<ResolvedPlayer> players = resolvePlayers(request.getPlayers());
        ResolvedTournament tournament = Boolean.TRUE.equals(request.getCreateTournament())
                ? resolveTournamentRequest(request.getTournament())
                : null;

        LeagueEntity league = leagueRepository.saveAndFlush(LeagueEntity.builder()
                .leagueName(basic.leagueName())
                .seasonName(basic.seasonName())
                .description(basic.description())
                .status(basic.status())
                .leagueType(basic.leagueType())
                .startDate(basic.startDate())
                .endDate(basic.endDate())
                .build());

        createParticipations(league.getId(), players);
        if (tournament != null) {
            TournamentDetailResponseDto created = tournamentCreationService.createTournament(
                    toTournamentCreateRequest(league, tournament, players),
                    ownerUserId
            );
            league.linkTournament(created.getId());
        }

        return getPersonalLeague(league.getId());
    }

    public AdminPersonalLeagueResponseDto updatePersonalLeague(
            Long leagueId,
            AdminPersonalLeagueCreateRequestDto request,
            Long ownerUserId
    ) {
        LeagueEntity league = requirePersonalLeague(leagueId);
        if (LeagueEntity.STATUS_FINISHED.equals(league.getStatus())) {
            throw new IllegalStateException("Finished personal leagues cannot be updated.");
        }

        BasicLeagueValues basic = normalizeBasic(request);
        requireSameLeagueType(league, basic.leagueType());
        List<ResolvedPlayer> players = resolvePlayers(request.getPlayers());
        boolean wantsTournament = Boolean.TRUE.equals(request.getCreateTournament());
        boolean hasTournament = league.getTournamentId() != null;
        if (hasTournament && !wantsTournament) {
            throw new IllegalStateException("Linked tournaments cannot be removed from a personal league.");
        }
        if (hasTournament && hasTournamentProgress(league.getTournamentId())) {
            throw new IllegalStateException("Personal leagues with progressed tournaments cannot be updated.");
        }

        ResolvedTournament tournament = wantsTournament
                ? resolveTournamentRequest(request.getTournament())
                : null;

        league.updateBasic(
                basic.leagueName(),
                basic.seasonName(),
                basic.description(),
                basic.status(),
                basic.startDate(),
                basic.endDate()
        );

        leagueParticipationRepository.deleteByLeagueId(league.getId());
        createParticipations(league.getId(), players);

        if (tournament != null) {
            if (league.getTournamentId() == null) {
                TournamentDetailResponseDto created = tournamentCreationService.createTournament(
                        toTournamentCreateRequest(league, tournament, players),
                        ownerUserId
                );
                league.linkTournament(created.getId());
            } else {
                tournamentCreationService.replaceTournament(
                        league.getTournamentId(),
                        toTournamentCreateRequest(league, tournament, players),
                        ownerUserId
                );
            }
        }

        return getPersonalLeague(leagueId);
    }

    @Transactional(readOnly = true)
    public AdminPersonalLeagueResponseDto getPersonalLeague(Long leagueId) {
        LeagueEntity league = requirePersonalLeague(leagueId);
        AdminPersonalLeagueResponseDto response = new AdminPersonalLeagueResponseDto();
        response.setId(league.getId());
        response.setLeagueName(league.getLeagueName());
        response.setSeasonName(league.getSeasonName());
        response.setDescription(league.getDescription());
        response.setStatus(league.getStatus());
        response.setLeagueType(league.getLeagueType());
        response.setStartDate(league.getStartDate());
        response.setEndDate(league.getEndDate());
        response.setTournamentId(league.getTournamentId());
        response.setTournamentBracketType(resolveTournamentBracketType(league.getTournamentId()));
        response.setTournamentBestOf(resolveTournamentBestOf(league.getTournamentId()));
        response.setCanEditTournament(canEditTournament(league));
        response.setPlayers(toPlayerResponses(league.getId()));
        response.setRegDate(league.getRegDate());
        response.setUpdateDate(league.getUpdateDate());
        return response;
    }

    private void createParticipations(Long leagueId, List<ResolvedPlayer> players) {
        for (ResolvedPlayer player : players) {
            leagueParticipationRepository.save(LeagueParticipationEntity.builder()
                    .leagueId(leagueId)
                    .userId(player.user().getId())
                    .race(defaultRace(player.user().getRace()))
                    .status(ACTIVE_USER_STATUS)
                    .build());
        }
    }

    private TournamentCreateRequestDto toTournamentCreateRequest(
            LeagueEntity league,
            ResolvedTournament tournament,
            List<ResolvedPlayer> players
    ) {
        TournamentCreateRequestDto request = new TournamentCreateRequestDto();
        request.setTitle(league.getLeagueName() + " Tournament");
        request.setBracketType(tournament.bracketType());
        request.setBestOf(tournament.bestOf());
        request.setPublishNow(true);
        request.setGroups(TournamentStageEntity.TYPE_DUAL_GROUP.equals(tournament.bracketType())
                ? toDualGroups(players)
                : List.of(toSingleGroup(players)));
        return request;
    }

    private TournamentCreateGroupRequestDto toSingleGroup(List<ResolvedPlayer> players) {
        TournamentCreateGroupRequestDto group = new TournamentCreateGroupRequestDto();
        group.setGroupCode(MAIN_GROUP_CODE);
        group.setGroupName(MAIN_GROUP_NAME);
        group.setSlots(toSlots(players, 0, players.size()));
        return group;
    }

    private List<TournamentCreateGroupRequestDto> toDualGroups(List<ResolvedPlayer> players) {
        List<TournamentCreateGroupRequestDto> groups = new ArrayList<>();
        int start = 0;
        List<Integer> groupSizes = dualGroupSizes(players.size());
        for (int groupIndex = 0; groupIndex < groupSizes.size(); groupIndex++) {
            int groupSize = groupSizes.get(groupIndex);
            String groupCode = defaultDualGroupCode(groupIndex);
            TournamentCreateGroupRequestDto group = new TournamentCreateGroupRequestDto();
            group.setGroupCode(groupCode);
            group.setGroupName(groupCode + " Group");
            group.setSlots(toDualSlots(players, start, groupSize));
            groups.add(group);
            start += groupSize;
        }
        return groups;
    }

    private List<Integer> dualGroupSizes(int playerCount) {
        List<Integer> groupSizes = new ArrayList<>();
        int remaining = playerCount;
        while (remaining > 0) {
            if (remaining <= DUAL_GROUP_SIZE) {
                groupSizes.add(remaining);
                break;
            }
            if (remaining == DUAL_GROUP_SIZE + 1) {
                groupSizes.add(3);
                groupSizes.add(2);
                break;
            }
            groupSizes.add(DUAL_GROUP_SIZE);
            remaining -= DUAL_GROUP_SIZE;
        }
        return groupSizes;
    }

    private List<TournamentCreateSlotRequestDto> toDualSlots(List<ResolvedPlayer> players, int start, int groupSize) {
        List<TournamentCreateSlotRequestDto> slots = new ArrayList<>();
        for (int offset = 0; offset < groupSize; offset++) {
            TournamentCreateSlotRequestDto slot = new TournamentCreateSlotRequestDto();
            slot.setSlotNo(groupSize == 2 && offset == 1 ? 3 : offset + 1);
            slot.setUserId(players.get(start + offset).user().getId());
            slots.add(slot);
        }
        return slots;
    }

    private List<TournamentCreateSlotRequestDto> toSlots(List<ResolvedPlayer> players, int start, int end) {
        List<TournamentCreateSlotRequestDto> slots = new ArrayList<>();
        for (int index = start; index < end; index++) {
            TournamentCreateSlotRequestDto slot = new TournamentCreateSlotRequestDto();
            slot.setSlotNo(index - start + 1);
            slot.setUserId(players.get(index).user().getId());
            slots.add(slot);
        }
        return slots;
    }

    private String defaultDualGroupCode(int groupIndex) {
        if (groupIndex < 26) {
            return String.valueOf((char) ('A' + groupIndex));
        }
        return "G" + (groupIndex + 1);
    }

    private List<ResolvedPlayer> resolvePlayers(List<AdminPersonalLeaguePlayerRequestDto> requests) {
        if (requests == null || requests.size() < 2) {
            throw new IllegalArgumentException("At least two players are required.");
        }

        Set<String> loginIds = new LinkedHashSet<>();
        List<ResolvedPlayer> players = new ArrayList<>();
        for (AdminPersonalLeaguePlayerRequestDto request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Player entry must not be null.");
            }
            String loginId = requireText(request.getUserId(), "Player userId is required.");
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

    private ResolvedTournament resolveTournamentRequest(AdminPersonalLeagueTournamentRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Tournament settings are required.");
        }
        String bracketType = requireText(request.getBracketType(), "bracketType is required.")
                .toUpperCase(Locale.ROOT);
        if (!BRACKET_TYPES.contains(bracketType)) {
            throw new IllegalArgumentException("bracketType must be SINGLE_ELIMINATION or DUAL_GROUP.");
        }
        int bestOf = request.getBestOf() == null ? DEFAULT_BEST_OF : request.getBestOf();
        if (bestOf < 1 || bestOf % 2 == 0) {
            throw new IllegalArgumentException("bestOf must be a positive odd number.");
        }
        return new ResolvedTournament(bracketType, bestOf);
    }

    private BasicLeagueValues normalizeBasic(AdminPersonalLeagueCreateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        String leagueName = requireText(request.getLeagueName(), "Personal league name is required.");
        if (leagueName.length() > 50) {
            throw new IllegalArgumentException("Personal league name must be 50 characters or less.");
        }
        String seasonName = trimToNull(request.getSeasonName());
        if (seasonName != null && seasonName.length() > 100) {
            throw new IllegalArgumentException("Season name must be 100 characters or less.");
        }
        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > 1000) {
            throw new IllegalArgumentException("Description must be 1000 characters or less.");
        }
        String status = normalizeReadyStatus(request.getStatus());
        String leagueType = normalizeExpectedLeagueType(request.getLeagueType(), LeagueEntity.TYPE_PERSONAL);
        return new BasicLeagueValues(
                leagueName,
                seasonName,
                description,
                status,
                leagueType,
                request.getStartDate(),
                request.getEndDate()
        );
    }

    private String normalizeExpectedLeagueType(String leagueType, String expectedLeagueType) {
        String normalized = requireText(leagueType, "leagueType is required.").toUpperCase(Locale.ROOT);
        if (!expectedLeagueType.equals(normalized)) {
            throw new IllegalArgumentException("leagueType must be " + expectedLeagueType + ".");
        }
        return normalized;
    }

    private void requireSameLeagueType(LeagueEntity league, String requestedLeagueType) {
        if (!Objects.equals(league.getLeagueType(), requestedLeagueType)) {
            throw new IllegalArgumentException("leagueType cannot be changed.");
        }
    }

    private String normalizeReadyStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? LeagueEntity.STATUS_READY
                : status.trim().toUpperCase(Locale.ROOT);
        if (!LEAGUE_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Personal league status must be READY, LIVE, or FINISHED.");
        }
        return normalized;
    }

    private boolean canEditTournament(LeagueEntity league) {
        if (LeagueEntity.STATUS_FINISHED.equals(league.getStatus())) {
            return false;
        }
        return league.getTournamentId() == null || !hasTournamentProgress(league.getTournamentId());
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
        return tournamentMatchRepository.countNonByeDecidedByStageIdIn(
                stageIds,
                TournamentMatchEntity.STATUS_FINISHED
        ) > 0
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

    private List<AdminPersonalLeaguePlayerResponseDto> toPlayerResponses(Long leagueId) {
        List<LeagueParticipationEntity> participations = leagueParticipationRepository.findAllByLeagueIdOrderByIdAsc(leagueId);
        Map<Long, UserEntity> usersById = loadUsers(participations.stream()
                .map(LeagueParticipationEntity::getUserId)
                .filter(Objects::nonNull)
                .toList());
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

    private Map<Long, UserEntity> loadUsers(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private LeagueEntity requirePersonalLeague(Long leagueId) {
        return leagueRepository.findByIdAndLeagueType(leagueId, LeagueEntity.TYPE_PERSONAL)
                .orElseThrow(() -> new NoSuchElementException("Personal league not found."));
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

    private record BasicLeagueValues(
            String leagueName,
            String seasonName,
            String description,
            String status,
            String leagueType,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    private record ResolvedPlayer(UserEntity user) {
    }

    private record ResolvedTournament(String bracketType, int bestOf) {
    }
}
