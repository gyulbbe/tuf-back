package io.github.gyulbbe.league.service;

import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.league.dto.AdminProleagueCandidateRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueCandidateResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueCreateRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueDeleteResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueDraftRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueFinishRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueHistoryPageResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueHistoryResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueHistoryTeamResponseDto;
import io.github.gyulbbe.league.dto.AdminProleaguePageResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueSummaryResponseDto;
import io.github.gyulbbe.league.dto.AdminProleagueTeamRequestDto;
import io.github.gyulbbe.league.dto.AdminProleagueTeamResponseDto;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueQueryRepository;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueHistoryCleanupRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminProleagueService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int DEFAULT_PICK_TIME_SECONDS = 30;
    private static final String DRAFT_STATUS_READY = "READY";
    private static final String ORDER_MODE_BASIC = "BASIC";
    private static final String ORDER_MODE_SNAKE = "SNAKE";
    private static final Set<String> LEAGUE_STATUSES = Set.of(
            LeagueEntity.STATUS_READY,
            LeagueEntity.STATUS_LIVE,
            LeagueEntity.STATUS_FINISHED
    );
    private static final Set<String> ORDER_MODES = Set.of(ORDER_MODE_BASIC, ORDER_MODE_SNAKE);

    private final LeagueRepository leagueRepository;
    private final ProleagueTeamRepository proleagueTeamRepository;
    private final LeagueParticipationRepository leagueParticipationRepository;
    private final LeagueQueryRepository leagueQueryRepository;
    private final ProleagueHistoryCleanupRepository proleagueHistoryCleanupRepository;
    private final DraftSessionRepository draftSessionRepository;
    private final DraftTeamRepository draftTeamRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final DraftOrderRepository draftOrderRepository;
    private final DraftPickRepository draftPickRepository;
    private final UserRepository userRepository;

    public AdminProleagueResponseDto createProleague(AdminProleagueCreateRequestDto request, Long ownerUserId) {
        BasicLeagueValues basic = normalizeBasic(request);
        LeagueEntity league = leagueRepository.saveAndFlush(LeagueEntity.builder()
                .leagueName(basic.leagueName())
                .seasonName(basic.seasonName())
                .description(basic.description())
                .status(basic.status())
                .startDate(basic.startDate())
                .endDate(basic.endDate())
                .build());

        if (Boolean.TRUE.equals(request.getCreateDraft())) {
            createDraftGraph(league, requireDraft(request.getDraft()), ownerUserId);
        }

        return getProleague(league.getId());
    }

    public AdminProleagueResponseDto updateProleague(Long leagueId, AdminProleagueCreateRequestDto request, Long ownerUserId) {
        LeagueEntity league = requireLeague(leagueId);
        if (!LeagueEntity.STATUS_READY.equals(league.getStatus())) {
            throw new IllegalStateException("READY 상태의 프로리그만 수정할 수 있습니다.");
        }

        BasicLeagueValues basic = normalizeBasic(request);
        league.updateBasic(
                basic.leagueName(),
                basic.seasonName(),
                basic.description(),
                basic.status(),
                basic.startDate(),
                basic.endDate()
        );

        boolean wantsDraftCreation = Boolean.TRUE.equals(request.getCreateDraft());
        boolean hasDraftPayload = request.getDraft() != null;

        if (league.getDraftSessionId() == null) {
            if (wantsDraftCreation) {
                createDraftGraph(league, requireDraft(request.getDraft()), ownerUserId);
            }
        } else if (hasDraftPayload) {
            replaceReadyDraftGraph(league, request.getDraft());
        }

        return getProleague(leagueId);
    }

    @Transactional(readOnly = true)
    public AdminProleagueResponseDto getProleague(Long leagueId) {
        LeagueEntity league = requireLeague(leagueId);
        DraftSessionEntity draftSession = findDraftSessionOrNull(league.getDraftSessionId());

        AdminProleagueResponseDto response = new AdminProleagueResponseDto();
        response.setId(league.getId());
        response.setLeagueName(league.getLeagueName());
        response.setSeasonName(league.getSeasonName());
        response.setDescription(league.getDescription());
        response.setStatus(league.getStatus());
        response.setStartDate(league.getStartDate());
        response.setEndDate(league.getEndDate());
        response.setDraftSessionId(league.getDraftSessionId());
        response.setDraftStatus(draftSession == null ? null : draftSession.getStatus());
        response.setCanEditDraft(canEditDraft(league, draftSession));
        response.setChampionTeamId(league.getChampionTeamId());
        response.setRunnerUpTeamId(league.getRunnerUpTeamId());
        response.setTeams(toTeamResponses(league.getId()));
        response.setCandidates(toCandidateResponses(league.getId()));
        response.setRegDate(league.getRegDate());
        response.setUpdateDate(league.getUpdateDate());
        return response;
    }

    @Transactional(readOnly = true)
    public AdminProleaguePageResponseDto listProleagues(int page, int size, String keyword, String status) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        String normalizedStatus = normalizeOptionalStatus(status);
        Page<Long> ids = leagueQueryRepository.findAdminProleagueIds(
                trimToNull(keyword),
                normalizedStatus,
                normalizedPage,
                normalizedSize
        );

        AdminProleaguePageResponseDto response = new AdminProleaguePageResponseDto();
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

    @Transactional(readOnly = true)
    public AdminProleagueHistoryPageResponseDto listHistory(
            int page,
            int size,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        Page<Long> ids = leagueQueryRepository.findAdminProleagueHistoryIds(
                trimToNull(keyword),
                fromDate,
                toDate,
                normalizedPage,
                normalizedSize
        );

        AdminProleagueHistoryPageResponseDto response = new AdminProleagueHistoryPageResponseDto();
        response.setItems(ids.getContent().stream()
                .map(this::toHistoryResponse)
                .toList());
        response.setPage(normalizedPage);
        response.setSize(normalizedSize);
        response.setTotalElements(ids.getTotalElements());
        response.setTotalPages(ids.getTotalPages());
        response.setHasNext(ids.hasNext());
        response.setHasPrevious(ids.hasPrevious());
        return response;
    }

    @Transactional(readOnly = true)
    public AdminProleagueHistoryResponseDto getHistory(Long leagueId) {
        LeagueEntity league = requireFinishedLeague(leagueId);
        return toHistoryResponse(league.getId());
    }

    public AdminProleagueHistoryResponseDto finishProleague(Long leagueId, AdminProleagueFinishRequestDto request) {
        LeagueEntity league = requireLeague(leagueId);
        if (request == null) {
            throw new IllegalArgumentException("Finish request is required.");
        }

        String championTeamName = requireText(request.getChampionTeamName(), "Champion team name is required.");
        String runnerUpTeamName = requireText(request.getRunnerUpTeamName(), "Runner-up team name is required.");
        if (championTeamName.equalsIgnoreCase(runnerUpTeamName)) {
            throw new IllegalArgumentException("Champion and runner-up teams must be different.");
        }

        List<ProleagueTeamEntity> teams = proleagueTeamRepository.findAllByLeagueIdOrderByDisplayOrderAscIdAsc(leagueId);
        ProleagueTeamEntity championTeam = findTeamByName(teams, championTeamName);
        ProleagueTeamEntity runnerUpTeam = findTeamByName(teams, runnerUpTeamName);
        league.finish(
                championTeam.getId(),
                runnerUpTeam.getId(),
                request.getEndDate() == null ? LocalDate.now() : request.getEndDate()
        );
        return toHistoryResponse(league.getId());
    }

    public AdminProleagueDeleteResponseDto deleteProleagues(List<Long> leagueIds) {
        if (leagueIds == null || leagueIds.isEmpty()) {
            throw new IllegalArgumentException("삭제할 프로리그를 선택해 주세요.");
        }

        List<Long> distinctIds = leagueIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("삭제할 프로리그를 선택해 주세요.");
        }

        for (Long leagueId : distinctIds) {
            deleteProleague(leagueId);
        }
        return new AdminProleagueDeleteResponseDto(distinctIds.size());
    }

    public AdminProleagueDeleteResponseDto deleteProleagueHistories(List<Long> leagueIds) {
        if (leagueIds == null || leagueIds.isEmpty()) {
            throw new IllegalArgumentException("Select proleague history to delete.");
        }

        List<Long> distinctIds = leagueIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("Select proleague history to delete.");
        }

        for (Long leagueId : distinctIds) {
            deleteProleagueHistory(leagueId);
        }
        return new AdminProleagueDeleteResponseDto(distinctIds.size());
    }

    private void createDraftGraph(LeagueEntity league, AdminProleagueDraftRequestDto request, Long ownerUserId) {
        ResolvedDraft resolved = resolveDraftRequest(request);
        DraftSessionEntity draftSession = draftSessionRepository.saveAndFlush(DraftSessionEntity.builder()
                .title(league.getLeagueName() + " 드래프트")
                .ownerUserId(ownerUserId)
                .status(DRAFT_STATUS_READY)
                .orderMode(resolved.orderMode())
                .teamCount(resolved.teamCount())
                .pickTimeSeconds(resolved.pickTimeSeconds())
                .currentPickNo(1)
                .build());

        createDraftChildren(league.getId(), draftSession.getId(), resolved);
        league.linkDraftSession(draftSession.getId());
    }

    private void replaceReadyDraftGraph(LeagueEntity league, AdminProleagueDraftRequestDto request) {
        DraftSessionEntity draftSession = requireDraftSession(league.getDraftSessionId());
        requireEditableDraft(draftSession);

        ResolvedDraft resolved = resolveDraftRequest(request);
        draftSession.update(
                league.getLeagueName() + " 드래프트",
                DRAFT_STATUS_READY,
                resolved.orderMode(),
                resolved.teamCount(),
                resolved.pickTimeSeconds(),
                1,
                null,
                null,
                null,
                null
        );
        draftSessionRepository.saveAndFlush(draftSession);
        proleagueTeamRepository.deleteByLeagueId(league.getId());
        leagueParticipationRepository.deleteByLeagueId(league.getId());
        draftOrderRepository.deleteByDraftSessionId(draftSession.getId());
        draftCandidateRepository.deleteByDraftSessionId(draftSession.getId());
        draftTeamRepository.deleteByDraftSessionId(draftSession.getId());
        createDraftChildren(league.getId(), draftSession.getId(), resolved);
    }

    private void createDraftChildren(Long leagueId, Long draftSessionId, ResolvedDraft resolved) {
        List<DraftTeamEntity> draftTeams = new ArrayList<>();
        for (ResolvedTeam team : resolved.teams()) {
            DraftTeamEntity draftTeam = draftTeamRepository.saveAndFlush(DraftTeamEntity.builder()
                    .draftSessionId(draftSessionId)
                    .teamName(team.teamName())
                    .displayOrder(team.displayOrder())
                    .pickerUserId(team.leader().getId())
                    .build());
            draftTeams.add(draftTeam);

            proleagueTeamRepository.save(ProleagueTeamEntity.builder()
                    .teamName(team.teamName())
                    .leagueId(leagueId)
                    .leaderId(team.leader().getId())
                    .viceLeaderId(team.viceLeader().getId())
                    .displayOrder(team.displayOrder())
                    .draftTeamId(draftTeam.getId())
                    .build());
        }

        for (UserEntity candidate : resolved.candidates()) {
            String race = defaultRace(candidate.getRace());
            leagueParticipationRepository.save(LeagueParticipationEntity.builder()
                    .leagueId(leagueId)
                    .userId(candidate.getId())
                    .race(race)
                    .status("ACTIVE")
                    .build());
            draftCandidateRepository.save(DraftCandidateEntity.builder()
                    .draftSessionId(draftSessionId)
                    .candidateUserId(candidate.getId())
                    .candidateName(candidate.getUserId())
                    .race(race)
                    .status("WAITING")
                    .build());
        }

        createDraftOrders(draftSessionId, resolved.orderMode(), draftTeams, resolved.candidates().size());
    }

    private void createDraftOrders(
            Long draftSessionId,
            String orderMode,
            List<DraftTeamEntity> draftTeams,
            int candidateCount
    ) {
        List<DraftTeamEntity> orderedTeams = draftTeams.stream()
                .sorted(Comparator.comparing(DraftTeamEntity::getDisplayOrder).thenComparing(DraftTeamEntity::getId))
                .toList();
        List<DraftOrderEntity> orders = new ArrayList<>();
        for (int pickNo = 1; pickNo <= candidateCount; pickNo++) {
            DraftTeamEntity draftTeam = orderedTeams.get(teamIndexForPick(pickNo, orderMode, orderedTeams.size()));
            orders.add(DraftOrderEntity.builder()
                    .draftSessionId(draftSessionId)
                    .pickNo((long) pickNo)
                    .draftTeamId(draftTeam.getId())
                    .build());
        }
        draftOrderRepository.saveAll(orders);
    }

    private int teamIndexForPick(int pickNo, String orderMode, int teamCount) {
        if (ORDER_MODE_SNAKE.equals(orderMode)) {
            int round = (pickNo - 1) / teamCount;
            int index = (pickNo - 1) % teamCount;
            return round % 2 == 0 ? index : teamCount - 1 - index;
        }
        return (pickNo - 1) % teamCount;
    }

    private void deleteProleague(Long leagueId) {
        LeagueEntity league = requireLeague(leagueId);
        league.clearResultTeams();
        leagueRepository.saveAndFlush(league);
        Long draftSessionId = league.getDraftSessionId();
        if (draftSessionId != null) {
            DraftSessionEntity draftSession = requireDraftSession(draftSessionId);
            requireEditableDraft(draftSession);
            league.unlinkDraftSession();
            leagueRepository.saveAndFlush(league);
            proleagueTeamRepository.unlinkDraftTeamsByLeagueId(leagueId);
            draftSession.clearCurrentDraftTeam();
            draftSessionRepository.saveAndFlush(draftSession);
            deleteDraftGraph(draftSessionId);
        }

        leagueParticipationRepository.deleteByLeagueId(leagueId);
        proleagueTeamRepository.deleteByLeagueId(leagueId);
        leagueRepository.delete(league);
    }

    private void deleteProleagueHistory(Long leagueId) {
        LeagueEntity league = requireFinishedLeague(leagueId);
        league.clearResultTeams();
        leagueRepository.saveAndFlush(league);

        Long draftSessionId = league.getDraftSessionId();
        if (draftSessionId != null) {
            league.unlinkDraftSession();
            leagueRepository.saveAndFlush(league);
            proleagueTeamRepository.unlinkDraftTeamsByLeagueId(leagueId);
            draftSessionRepository.findById(draftSessionId)
                    .ifPresent(draftSession -> {
                        draftSession.clearCurrentDraftTeam();
                        draftSessionRepository.saveAndFlush(draftSession);
                    });
        }

        proleagueHistoryCleanupRepository.deleteCommentariesByLeagueId(leagueId);
        proleagueHistoryCleanupRepository.deleteMatchPlayersByLeagueId(leagueId);
        proleagueHistoryCleanupRepository.deleteMatchInfosByLeagueId(leagueId);
        proleagueHistoryCleanupRepository.deleteSeriesInfosByLeagueId(leagueId);

        if (draftSessionId != null) {
            deleteDraftGraph(draftSessionId);
        }

        leagueParticipationRepository.deleteByLeagueId(leagueId);
        proleagueTeamRepository.deleteByLeagueId(leagueId);
        leagueRepository.delete(league);
    }

    private void deleteDraftGraph(Long draftSessionId) {
        draftPickRepository.deleteByDraftSessionId(draftSessionId);
        draftOrderRepository.deleteByDraftSessionId(draftSessionId);
        draftCandidateRepository.deleteByDraftSessionId(draftSessionId);
        draftTeamRepository.deleteByDraftSessionId(draftSessionId);
        draftSessionRepository.deleteById(draftSessionId);
    }

    private ResolvedDraft resolveDraftRequest(AdminProleagueDraftRequestDto request) {
        int teamCount = requirePositiveTeamCount(request.getTeamCount());
        int pickTimeSeconds = request.getPickTimeSeconds() == null
                ? DEFAULT_PICK_TIME_SECONDS
                : request.getPickTimeSeconds();
        if (pickTimeSeconds <= 0) {
            throw new IllegalArgumentException("픽 제한 시간은 1초 이상이어야 합니다.");
        }

        String orderMode = normalizeOrderMode(request.getOrderMode());
        List<AdminProleagueTeamRequestDto> teamRequests = request.getTeams() == null
                ? List.of()
                : request.getTeams();
        if (teamRequests.size() != teamCount) {
            throw new IllegalArgumentException("드래프트 팀 수와 팀 목록 수가 일치해야 합니다.");
        }
        List<AdminProleagueCandidateRequestDto> candidateRequests = request.getCandidates() == null
                ? List.of()
                : request.getCandidates();
        if (candidateRequests.isEmpty()) {
            throw new IllegalArgumentException("드래프트 후보는 1명 이상 필요합니다.");
        }

        List<ResolvedTeam> teams = resolveTeams(teamRequests);
        List<UserEntity> candidates = resolveCandidates(candidateRequests);
        return new ResolvedDraft(teamCount, pickTimeSeconds, orderMode, teams, candidates);
    }

    private List<ResolvedTeam> resolveTeams(List<AdminProleagueTeamRequestDto> requests) {
        Set<Long> leaderIds = new LinkedHashSet<>();
        Set<Long> viceLeaderIds = new LinkedHashSet<>();
        Set<Integer> displayOrders = new LinkedHashSet<>();
        Set<String> teamNames = new LinkedHashSet<>();
        List<ResolvedTeam> teams = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            AdminProleagueTeamRequestDto request = requests.get(i);
            if (request == null) {
                throw new IllegalArgumentException("팀 설정은 비어 있을 수 없습니다.");
            }
            String teamName = requireText(request.getTeamName(), "팀 이름은 필수입니다.");
            String normalizedTeamName = teamName.toUpperCase(Locale.ROOT);
            if (!teamNames.add(normalizedTeamName)) {
                throw new IllegalArgumentException("팀 이름이 중복되었습니다.");
            }
            UserEntity leader = requireUserByLoginId(request.getLeaderUserId(), "팀장을 찾을 수 없습니다.");
            UserEntity viceLeader = requireUserByLoginId(request.getViceLeaderUserId(), "부팀장을 찾을 수 없습니다.");
            if (leader.getId().equals(viceLeader.getId())) {
                throw new IllegalArgumentException("팀장과 부팀장은 서로 달라야 합니다.");
            }
            if (!leaderIds.add(leader.getId())) {
                throw new IllegalArgumentException("팀장이 중복되었습니다.");
            }
            if (!viceLeaderIds.add(viceLeader.getId())) {
                throw new IllegalArgumentException("부팀장이 중복되었습니다.");
            }

            int displayOrder = request.getDisplayOrder() == null ? i + 1 : request.getDisplayOrder();
            if (displayOrder <= 0) {
                throw new IllegalArgumentException("팀 순서는 1 이상이어야 합니다.");
            }
            if (!displayOrders.add(displayOrder)) {
                throw new IllegalArgumentException("팀 순서가 중복되었습니다.");
            }
            teams.add(new ResolvedTeam(teamName, leader, viceLeader, displayOrder));
        }

        return teams.stream()
                .sorted(Comparator.comparing(ResolvedTeam::displayOrder))
                .toList();
    }

    private List<UserEntity> resolveCandidates(List<AdminProleagueCandidateRequestDto> requests) {
        Set<Long> candidateIds = new LinkedHashSet<>();
        List<UserEntity> candidates = new ArrayList<>();
        for (AdminProleagueCandidateRequestDto request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("후보 설정은 비어 있을 수 없습니다.");
            }
            UserEntity candidate = requireUserByLoginId(request.getUserId(), "후보를 찾을 수 없습니다.");
            if (!candidateIds.add(candidate.getId())) {
                throw new IllegalArgumentException("후보가 중복되었습니다.");
            }
            candidates.add(candidate);
        }
        return candidates;
    }

    private BasicLeagueValues normalizeBasic(AdminProleagueCreateRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 값이 없습니다.");
        }
        String leagueName = requireText(request.getLeagueName(), "프로리그 이름은 필수입니다.");
        if (leagueName.length() > 50) {
            throw new IllegalArgumentException("프로리그 이름은 50자 이하여야 합니다.");
        }
        String seasonName = trimToNull(request.getSeasonName());
        if (seasonName != null && seasonName.length() > 100) {
            throw new IllegalArgumentException("시즌명은 100자 이하여야 합니다.");
        }
        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > 1000) {
            throw new IllegalArgumentException("설명은 1000자 이하여야 합니다.");
        }
        String status = normalizeRequiredStatus(request.getStatus());
        return new BasicLeagueValues(
                leagueName,
                seasonName,
                description,
                status,
                request.getStartDate(),
                request.getEndDate()
        );
    }

    private AdminProleagueDraftRequestDto requireDraft(AdminProleagueDraftRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("드래프트 설정이 필요합니다.");
        }
        return request;
    }

    private int requirePositiveTeamCount(Integer teamCount) {
        if (teamCount == null || teamCount < 2) {
            throw new IllegalArgumentException("드래프트 팀 수는 2 이상이어야 합니다.");
        }
        return teamCount;
    }

    private String normalizeOrderMode(String orderMode) {
        String normalized = orderMode == null || orderMode.isBlank()
                ? ORDER_MODE_BASIC
                : orderMode.trim().toUpperCase(Locale.ROOT);
        if (!ORDER_MODES.contains(normalized)) {
            throw new IllegalArgumentException("드래프트 순서 방식은 BASIC 또는 SNAKE만 가능합니다.");
        }
        return normalized;
    }

    private String normalizeRequiredStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? LeagueEntity.STATUS_READY
                : status.trim().toUpperCase(Locale.ROOT);
        if (!LEAGUE_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("프로리그 상태는 READY, LIVE, FINISHED만 가능합니다.");
        }
        return normalized;
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!LEAGUE_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("프로리그 상태는 READY, LIVE, FINISHED만 가능합니다.");
        }
        return normalized;
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

    private UserEntity requireUserByLoginId(String userId, String message) {
        String loginId = requireText(userId, message);
        UserEntity user = userRepository.findByUserIdIgnoreCase(loginId);
        if (user == null) {
            throw new IllegalArgumentException(message + " userId=" + loginId);
        }
        return user;
    }

    private LeagueEntity requireLeague(Long leagueId) {
        return leagueRepository.findById(leagueId)
                .orElseThrow(() -> new NoSuchElementException("프로리그를 찾을 수 없습니다."));
    }

    private LeagueEntity requireFinishedLeague(Long leagueId) {
        LeagueEntity league = requireLeague(leagueId);
        if (!LeagueEntity.STATUS_FINISHED.equals(league.getStatus())) {
            throw new IllegalStateException("Only finished proleague history can be used.");
        }
        return league;
    }

    private DraftSessionEntity requireDraftSession(Long draftSessionId) {
        return draftSessionRepository.findById(draftSessionId)
                .orElseThrow(() -> new NoSuchElementException("연결된 드래프트를 찾을 수 없습니다."));
    }

    private DraftSessionEntity findDraftSessionOrNull(Long draftSessionId) {
        if (draftSessionId == null) {
            return null;
        }
        return draftSessionRepository.findById(draftSessionId).orElse(null);
    }

    private void requireEditableDraft(DraftSessionEntity draftSession) {
        if (!DRAFT_STATUS_READY.equals(draftSession.getStatus())) {
            throw new IllegalStateException("시작된 드래프트는 수정하거나 삭제할 수 없습니다.");
        }
        if (draftPickRepository.countByDraftSessionId(draftSession.getId()) > 0) {
            throw new IllegalStateException("픽이 발생한 드래프트는 수정하거나 삭제할 수 없습니다.");
        }
    }

    private boolean canEditDraft(LeagueEntity league, DraftSessionEntity draftSession) {
        if (!LeagueEntity.STATUS_READY.equals(league.getStatus())) {
            return false;
        }
        if (draftSession == null) {
            return true;
        }
        return DRAFT_STATUS_READY.equals(draftSession.getStatus())
                && draftPickRepository.countByDraftSessionId(draftSession.getId()) == 0;
    }

    private List<AdminProleagueTeamResponseDto> toTeamResponses(Long leagueId) {
        List<ProleagueTeamEntity> teams = proleagueTeamRepository.findAllByLeagueIdOrderByDisplayOrderAscIdAsc(leagueId);
        Map<Long, UserEntity> usersById = loadUsers(teams.stream()
                .flatMap(team -> Stream.of(team.getLeaderId(), team.getViceLeaderId()))
                .filter(Objects::nonNull)
                .toList());
        return teams.stream()
                .map(team -> {
                    AdminProleagueTeamResponseDto dto = new AdminProleagueTeamResponseDto();
                    dto.setId(team.getId());
                    dto.setTeamName(team.getTeamName());
                    dto.setLeaderUserId(loginId(usersById.get(team.getLeaderId())));
                    dto.setViceLeaderUserId(loginId(usersById.get(team.getViceLeaderId())));
                    dto.setDisplayOrder(team.getDisplayOrder());
                    dto.setDraftTeamId(team.getDraftTeamId());
                    return dto;
                })
                .toList();
    }

    private List<AdminProleagueCandidateResponseDto> toCandidateResponses(Long leagueId) {
        List<LeagueParticipationEntity> participations = leagueParticipationRepository.findAllByLeagueIdOrderByIdAsc(leagueId);
        Map<Long, UserEntity> usersById = loadUsers(participations.stream()
                .map(LeagueParticipationEntity::getUserId)
                .filter(Objects::nonNull)
                .toList());
        return participations.stream()
                .map(participation -> {
                    AdminProleagueCandidateResponseDto dto = new AdminProleagueCandidateResponseDto();
                    UserEntity user = usersById.get(participation.getUserId());
                    dto.setUserId(loginId(user));
                    dto.setRace(participation.getRace());
                    dto.setStatus(participation.getStatus());
                    return dto;
                })
                .toList();
    }

    private AdminProleagueSummaryResponseDto toSummaryResponse(Long leagueId) {
        LeagueEntity league = requireLeague(leagueId);
        DraftSessionEntity draftSession = findDraftSessionOrNull(league.getDraftSessionId());
        AdminProleagueSummaryResponseDto dto = new AdminProleagueSummaryResponseDto();
        dto.setId(league.getId());
        dto.setLeagueName(league.getLeagueName());
        dto.setSeasonName(league.getSeasonName());
        dto.setStatus(league.getStatus());
        dto.setStartDate(league.getStartDate());
        dto.setEndDate(league.getEndDate());
        dto.setDraftSessionId(league.getDraftSessionId());
        dto.setDraftStatus(draftSession == null ? null : draftSession.getStatus());
        dto.setCanEditDraft(canEditDraft(league, draftSession));
        dto.setTeamCount(proleagueTeamRepository.countByLeagueId(leagueId));
        dto.setCandidateCount(leagueParticipationRepository.countByLeagueId(leagueId));
        dto.setUpdateDate(league.getUpdateDate());
        return dto;
    }

    private AdminProleagueHistoryResponseDto toHistoryResponse(Long leagueId) {
        LeagueEntity league = requireLeague(leagueId);
        DraftSessionEntity draftSession = findDraftSessionOrNull(league.getDraftSessionId());
        Map<Long, ProleagueTeamEntity> teamsById = proleagueTeamRepository.findAllByLeagueIdOrderByDisplayOrderAscIdAsc(leagueId)
                .stream()
                .collect(Collectors.toMap(ProleagueTeamEntity::getId, Function.identity(), (left, right) -> left));

        AdminProleagueHistoryResponseDto dto = new AdminProleagueHistoryResponseDto();
        dto.setId(league.getId());
        dto.setLeagueName(league.getLeagueName());
        dto.setSeasonName(league.getSeasonName());
        dto.setDescription(league.getDescription());
        dto.setStatus(league.getStatus());
        dto.setStartDate(league.getStartDate());
        dto.setEndDate(league.getEndDate());
        dto.setDraftSessionId(league.getDraftSessionId());
        dto.setDraftStatus(draftSession == null ? null : draftSession.getStatus());
        dto.setChampionTeamId(league.getChampionTeamId());
        dto.setChampionTeamName(teamName(teamsById.get(league.getChampionTeamId())));
        dto.setRunnerUpTeamId(league.getRunnerUpTeamId());
        dto.setRunnerUpTeamName(teamName(teamsById.get(league.getRunnerUpTeamId())));
        dto.setTeamCount((long) teamsById.size());
        dto.setParticipantCount(resolveParticipantCount(league));
        dto.setTeams(toHistoryTeamResponses(new ArrayList<>(teamsById.values())));
        dto.setUpdateDate(league.getUpdateDate());
        return dto;
    }

    private List<AdminProleagueHistoryTeamResponseDto> toHistoryTeamResponses(List<ProleagueTeamEntity> teams) {
        teams.sort(Comparator.comparing(ProleagueTeamEntity::getDisplayOrder).thenComparing(ProleagueTeamEntity::getId));
        Map<Long, UserEntity> usersById = loadUsers(teams.stream()
                .flatMap(team -> Stream.of(team.getLeaderId(), team.getViceLeaderId()))
                .filter(Objects::nonNull)
                .toList());
        return teams.stream()
                .map(team -> {
                    AdminProleagueHistoryTeamResponseDto dto = new AdminProleagueHistoryTeamResponseDto();
                    dto.setTeamId(team.getId());
                    dto.setTeamName(team.getTeamName());
                    dto.setLeaderUserId(loginId(usersById.get(team.getLeaderId())));
                    dto.setViceLeaderUserId(loginId(usersById.get(team.getViceLeaderId())));
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

    private String loginId(UserEntity user) {
        return user == null ? null : user.getUserId();
    }

    private ProleagueTeamEntity findTeamByName(List<ProleagueTeamEntity> teams, String teamName) {
        return teams.stream()
                .filter(team -> team.getTeamName() != null && team.getTeamName().equalsIgnoreCase(teamName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Team not found in this proleague. teamName=" + teamName));
    }

    private String teamName(ProleagueTeamEntity team) {
        return team == null ? null : team.getTeamName();
    }

    private long resolveParticipantCount(LeagueEntity league) {
        long activeCount = leagueParticipationRepository.countByLeagueIdAndStatus(league.getId(), "ACTIVE");
        if (activeCount > 0 || league.getDraftSessionId() == null) {
            return activeCount;
        }
        return draftCandidateRepository.countByDraftSessionId(league.getDraftSessionId());
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

    private record BasicLeagueValues(
            String leagueName,
            String seasonName,
            String description,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    private record ResolvedDraft(
            int teamCount,
            int pickTimeSeconds,
            String orderMode,
            List<ResolvedTeam> teams,
            List<UserEntity> candidates
    ) {
    }

    private record ResolvedTeam(
            String teamName,
            UserEntity leader,
            UserEntity viceLeader,
            int displayOrder
    ) {
    }
}
