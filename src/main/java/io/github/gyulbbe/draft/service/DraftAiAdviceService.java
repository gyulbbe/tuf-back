package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.chat.provider.ChatProviderRouter;
import io.github.gyulbbe.draft.dto.DraftAiAdviceResponseDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveCurrentTurnResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
import io.github.gyulbbe.draft.dto.DraftLiveRosterItemResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveSessionInfoResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveTeamResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftAiAdviceService {

    private static final String REVIEW_SYSTEM_PROMPT = String.join("\n",
            "너는 스타크래프트 프로리그 드래프트 훈수 두는 AI다.",
            "반드시 한국어로만 답한다.",
            "너는 디씨인사이드 유저다.",
            "격식 차리지 말고 짧게 말한다.",
            "없는 정보는 지어내지 않는다.",
            "서버가 제공한 드래프트 상태만 근거로 말한다.",
            "방금 지명에 대한 평가와 훈수만 말한다.",
            "다음 픽 추천은 말하지 않는다.",
            "답변은 최대 2문장으로 제한한다."
    );

    private static final String RECOMMENDATION_SYSTEM_PROMPT = String.join("\n",
            "너는 스타크래프트 프로리그 드래프트 훈수 두는 AI다.",
            "반드시 한국어로만 답한다.",
            "너는 디씨인사이드 유저다.",
            "격식 차리지 말고 짧게 말한다.",
            "없는 정보는 지어내지 않는다.",
            "서버가 제공한 드래프트 상태만 근거로 말한다.",
            "현재 픽커에게 남은 후보 중 누구를 뽑으면 좋을지만 추천한다.",
            "추천 선수 이름을 반드시 포함한다.",
            "티어, 종족 밸런스, 현재 팀 로스터, 남은 후보 구성을 근거로 판단한다.",
            "답변은 최대 2문장으로 제한한다."
    );

    private static final int AVAILABLE_CANDIDATE_SUMMARY_LIMIT = 10;
    private static final Duration CONTEXT_TTL = Duration.ofHours(6);

    private final DraftSnapshotService draftSnapshotService;
    private final DraftEventPublisher draftEventPublisher;
    private final ChatProviderRouter chatProviderRouter;
    private final ConcurrentHashMap<Long, DraftAiDraftContext> contexts = new ConcurrentHashMap<>();

    public void scheduleAdviceAfterPick(Long sessionId) {
        if (sessionId == null) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runAsync(sessionId);
                }
            });
            return;
        }

        runAsync(sessionId);
    }

    public void evictContext(Long sessionId) {
        if (sessionId == null) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    removeContext(sessionId);
                }
            });
            return;
        }

        removeContext(sessionId);
    }

    void generateAndPublishAdvice(Long sessionId) {
        try {
            evictExpiredContexts();

            DraftLiveSnapshotResponseDto initialSnapshot = draftSnapshotService.getBroadcastSnapshot(sessionId);
            DraftAiDraftContext draftContext = getOrInitializeContext(sessionId, initialSnapshot);
            Optional<PickReviewContext> reviewContext = buildPickReviewContext(initialSnapshot, draftContext);
            if (reviewContext.isEmpty()) {
                return;
            }

            PickReviewContext context = reviewContext.get();
            String reviewMessage = chatProviderRouter.chat(REVIEW_SYSTEM_PROMPT, buildReviewPrompt(context));
            if (reviewMessage == null || reviewMessage.isBlank()) {
                log.warn("Draft AI pick review returned empty response. sessionId={}", sessionId);
                return;
            }
            draftEventPublisher.publishAiAdvice(
                    sessionId,
                    DraftLiveEventType.AI_PICK_REVIEW_READY,
                    buildPickReviewAdvice(context, reviewMessage.trim())
            );

            DraftLiveSnapshotResponseDto recommendationSnapshot = draftSnapshotService.getBroadcastSnapshot(sessionId);
            Optional<RecommendationContext> recommendationContext = buildRecommendationContext(recommendationSnapshot, context);
            if (recommendationContext.isEmpty()) {
                return;
            }

            RecommendationContext recommendation = recommendationContext.get();
            String recommendationMessage = chatProviderRouter.chat(
                    RECOMMENDATION_SYSTEM_PROMPT,
                    buildRecommendationPrompt(recommendation)
            );
            if (recommendationMessage == null || recommendationMessage.isBlank()) {
                log.warn("Draft AI recommendation returned empty response. sessionId={}", sessionId);
                return;
            }
            if (!isRecommendationStillCurrent(sessionId, recommendation)) {
                return;
            }
            draftEventPublisher.publishAiAdvice(
                    sessionId,
                    DraftLiveEventType.AI_RECOMMENDATION_READY,
                    buildRecommendationAdvice(recommendation, recommendationMessage.trim())
            );
        } catch (Exception e) {
            log.warn("Draft AI advice generation failed. sessionId={}, reason={}", sessionId, e.getMessage(), e);
        }
    }

    Optional<PickReviewContext> buildPickReviewContext(
            DraftLiveSnapshotResponseDto snapshot,
            DraftAiDraftContext draftContext
    ) {
        if (snapshot == null || snapshot.getSession() == null || draftContext == null) {
            return Optional.empty();
        }

        DraftLiveSessionInfoResponseDto session = snapshot.getSession();
        if ("FINISHED".equals(session.getStatus())) {
            return Optional.empty();
        }
        if (snapshot.getCurrentTurn() == null) {
            return Optional.empty();
        }
        if (snapshot.getRecentPicks() == null || snapshot.getRecentPicks().isEmpty()) {
            return Optional.empty();
        }

        DraftPickResponseDto justPicked = snapshot.getRecentPicks().get(0);
        if (justPicked.getPickNo() == null || session.getTeamCount() == null || session.getTeamCount() <= 0) {
            return Optional.empty();
        }
        long adviceThresholdPickNo = session.getTeamCount() * 2L;
        if (justPicked.getPickNo() <= adviceThresholdPickNo) {
            return Optional.empty();
        }

        DraftLiveCurrentTurnResponseDto currentTurn = snapshot.getCurrentTurn();
        TeamAiState evaluatedTeam = draftContext.findTeam(justPicked.getDraftTeamId()).orElse(null);
        Long expectedNextPickNo = justPicked.getPickNo() + 1L;
        return Optional.of(new PickReviewContext(
                draftContext,
                justPicked,
                evaluatedTeam,
                expectedNextPickNo,
                currentTurn.getTeamId()
        ));
    }

    Optional<RecommendationContext> buildRecommendationContext(
            DraftLiveSnapshotResponseDto snapshot,
            PickReviewContext reviewContext
    ) {
        if (snapshot == null || snapshot.getSession() == null || reviewContext == null) {
            return Optional.empty();
        }

        DraftLiveSessionInfoResponseDto session = snapshot.getSession();
        if ("FINISHED".equals(session.getStatus())) {
            return Optional.empty();
        }
        if (snapshot.getCurrentTurn() == null) {
            return Optional.empty();
        }
        if (snapshot.getRecentPicks() == null || snapshot.getRecentPicks().isEmpty()) {
            return Optional.empty();
        }
        if (!Objects.equals(snapshot.getRecentPicks().get(0).getPickNo(), reviewContext.justPicked().getPickNo())) {
            return Optional.empty();
        }
        if (!Objects.equals(snapshot.getCurrentTurn().getPickNo(), reviewContext.expectedNextPickNo())) {
            return Optional.empty();
        }
        if (!Objects.equals(snapshot.getCurrentTurn().getTeamId(), reviewContext.expectedRecommendedTeamId())) {
            return Optional.empty();
        }
        if (snapshot.getAvailableCandidates() == null || snapshot.getAvailableCandidates().isEmpty()) {
            return Optional.empty();
        }

        TeamAiState currentTeam = reviewContext.draftContext()
                .findTeam(snapshot.getCurrentTurn().getTeamId())
                .orElse(null);
        List<CandidateAiState> ranked = rankAvailableCandidates(
                reviewContext.draftContext().availableCandidates(),
                currentTeam
        );
        if (ranked.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RecommendationContext(
                reviewContext.draftContext(),
                reviewContext.justPicked(),
                snapshot.getCurrentTurn(),
                currentTeam,
                ranked.get(0)
        ));
    }

    private boolean isRecommendationStillCurrent(Long sessionId, RecommendationContext context) {
        DraftLiveSnapshotResponseDto snapshot = draftSnapshotService.getBroadcastSnapshot(sessionId);
        if (snapshot == null || snapshot.getSession() == null) {
            return false;
        }
        if ("FINISHED".equals(snapshot.getSession().getStatus())) {
            return false;
        }
        if (snapshot.getCurrentTurn() == null) {
            return false;
        }
        if (snapshot.getRecentPicks() == null || snapshot.getRecentPicks().isEmpty()) {
            return false;
        }
        if (snapshot.getAvailableCandidates() == null || snapshot.getAvailableCandidates().isEmpty()) {
            return false;
        }

        return Objects.equals(snapshot.getRecentPicks().get(0).getPickNo(), context.justPicked().getPickNo())
                && Objects.equals(snapshot.getCurrentTurn().getPickNo(), context.currentTurn().getPickNo())
                && Objects.equals(snapshot.getCurrentTurn().getTeamId(), context.currentTurn().getTeamId());
    }

    boolean hasContext(Long sessionId) {
        return contexts.containsKey(sessionId);
    }

    Long lastAppliedPickNo(Long sessionId) {
        DraftAiDraftContext context = contexts.get(sessionId);
        return context == null ? null : context.lastAppliedPickNo();
    }

    int availableCandidateCount(Long sessionId) {
        DraftAiDraftContext context = contexts.get(sessionId);
        return context == null ? 0 : context.availableCandidateCount();
    }

    int contextIdentityHash(Long sessionId) {
        DraftAiDraftContext context = contexts.get(sessionId);
        return context == null ? 0 : System.identityHashCode(context);
    }

    private DraftAiDraftContext getOrInitializeContext(Long sessionId, DraftLiveSnapshotResponseDto snapshot) {
        if (sessionId == null || snapshot == null || snapshot.getSession() == null) {
            return null;
        }
        if ("FINISHED".equals(snapshot.getSession().getStatus())) {
            removeContext(sessionId);
            return null;
        }

        DraftAiDraftContext context = contexts.compute(sessionId, (key, existing) -> {
            if (existing == null || !existing.isCompatibleWith(snapshot)) {
                return DraftAiDraftContext.fromSnapshot(sessionId, snapshot);
            }
            existing.applyLatestPick(snapshot);
            return existing;
        });
        return context == null ? null : context.copy();
    }

    private void evictExpiredContexts() {
        Instant now = Instant.now();
        List<Long> expiredSessionIds = contexts.entrySet()
                .stream()
                .filter(entry -> entry.getValue().isExpired(now))
                .map(Map.Entry::getKey)
                .toList();
        expiredSessionIds.forEach(this::removeContext);
    }

    private void removeContext(Long sessionId) {
        contexts.remove(sessionId);
    }

    private void runAsync(Long sessionId) {
        CompletableFuture.runAsync(() -> generateAndPublishAdvice(sessionId))
                .exceptionally(e -> {
                    log.warn("Draft AI advice async task failed. sessionId={}, reason={}", sessionId, e.getMessage(), e);
                    return null;
                });
    }

    private DraftAiAdviceResponseDto buildPickReviewAdvice(PickReviewContext context, String message) {
        return DraftAiAdviceResponseDto.builder()
                .pickNo(context.justPicked().getPickNo())
                .evaluatedTeamId(context.justPicked().getDraftTeamId())
                .evaluatedTeamName(context.justPicked().getDraftTeamName())
                .evaluatedCandidateUserId(context.justPicked().getCandidateUserId())
                .evaluatedCandidateName(displayCandidateName(context.justPicked()))
                .message(message)
                .build();
    }

    private DraftAiAdviceResponseDto buildRecommendationAdvice(RecommendationContext context, String message) {
        return DraftAiAdviceResponseDto.builder()
                .pickNo(context.justPicked().getPickNo())
                .evaluatedTeamId(context.justPicked().getDraftTeamId())
                .evaluatedTeamName(context.justPicked().getDraftTeamName())
                .evaluatedCandidateUserId(context.justPicked().getCandidateUserId())
                .evaluatedCandidateName(displayCandidateName(context.justPicked()))
                .nextPickNo(context.currentTurn().getPickNo())
                .recommendedTeamId(context.currentTurn().getTeamId())
                .recommendedTeamName(context.currentTurn().getTeamName())
                .recommendedCandidateUserId(context.recommendedCandidate().candidateUserId())
                .recommendedCandidateName(displayCandidateName(context.recommendedCandidate()))
                .message(message)
                .build();
    }

    private String buildReviewPrompt(PickReviewContext context) {
        DraftPickResponseDto justPicked = context.justPicked();

        return String.join("\n",
                "방금 완료된 드래프트 지명을 평가해줘.",
                "",
                "[방금 지명]",
                "픽 번호: " + valueOrUnknown(justPicked.getPickNo()),
                "팀: " + valueOrUnknown(justPicked.getDraftTeamName()),
                "선수: " + displayCandidateName(justPicked),
                "티어: " + valueOrUnknown(justPicked.getTier()),
                "종족: " + valueOrUnknown(justPicked.getRace()),
                "",
                "[지명 팀 로스터]",
                teamRosterSummary(context.evaluatedTeam()),
                "",
                "[전체 팀 종족/티어 현황]",
                teamBalanceSummary(context.draftContext()),
                "",
                "출력 조건:",
                "- 방금 지명 평가와 훈수만 말한다.",
                "- 다음 픽 추천은 말하지 않는다.",
                "- 1~2문장.",
                "- JSON 말고 자연어만 출력."
        );
    }

    private String buildRecommendationPrompt(RecommendationContext context) {
        CandidateAiState recommendedCandidate = context.recommendedCandidate();
        DraftLiveCurrentTurnResponseDto currentTurn = context.currentTurn();

        return String.join("\n",
                "현재 픽커에게 다음 지명 추천을 해줘.",
                "",
                "[현재 추천 대상]",
                "다음 픽 번호: " + valueOrUnknown(currentTurn.getPickNo()),
                "현재 픽커 팀: " + valueOrUnknown(currentTurn.getTeamName()),
                "",
                "[서버 추천 후보]",
                "추천 선수: " + displayCandidateName(recommendedCandidate),
                "티어: " + valueOrUnknown(recommendedCandidate.tier()),
                "종족: " + valueOrUnknown(recommendedCandidate.race()),
                "",
                "[현재 픽커 팀 로스터]",
                teamRosterSummary(context.currentTeam()),
                "",
                "[전체 팀 종족/티어 현황]",
                teamBalanceSummary(context.draftContext()),
                "",
                "[남은 후보 Top 후보]",
                availableCandidateSummary(context.draftContext(), context.currentTeam()),
                "",
                "출력 조건:",
                "- 현재 픽커에게 추천할 선수와 이유만 말한다.",
                "- 추천 선수 이름을 명확히 포함.",
                "- 방금 지명 평가는 반복하지 않는다.",
                "- 1~2문장.",
                "- JSON 말고 자연어만 출력."
        );
    }

    private List<CandidateAiState> rankAvailableCandidates(
            Collection<CandidateAiState> availableCandidates,
            TeamAiState currentTeam
    ) {
        List<CandidateAiState> ranked = new ArrayList<>(availableCandidates);
        ranked.sort(Comparator
                .comparingInt((CandidateAiState candidate) -> currentTeamRaceCount(currentTeam, candidate.race()))
                .thenComparingInt(candidate -> parseTier(candidate.tier()))
                .thenComparing(candidate -> displayCandidateName(candidate), Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(CandidateAiState::candidateUserId, Comparator.nullsLast(Long::compareTo)));
        return ranked;
    }

    private String teamRosterSummary(TeamAiState team) {
        if (team == null || team.roster().isEmpty()) {
            return "아직 지명한 선수가 없습니다.";
        }

        return team.roster().stream()
                .sorted(Comparator.comparing(RosterAiState::pickNo, Comparator.nullsLast(Long::compareTo)))
                .map(item -> displayCandidateName(item)
                        + " (티어 " + valueOrUnknown(item.tier())
                        + ", 종족 " + valueOrUnknown(item.race()) + ")")
                .collect(Collectors.joining("\n"));
    }

    private String teamBalanceSummary(DraftAiDraftContext context) {
        if (context == null || context.teams().isEmpty()) {
            return "팀 정보가 없습니다.";
        }

        return context.teams().stream()
                .map(team -> valueOrUnknown(team.teamName()) + ": " + summarizeRosterBalance(team))
                .collect(Collectors.joining("\n"));
    }

    private String summarizeRosterBalance(TeamAiState team) {
        if (team.roster().isEmpty()) {
            return "0명";
        }

        String races = team.raceCounts().entrySet()
                .stream()
                .sorted(MapEntryComparator.INSTANCE)
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));
        String tiers = team.tierCounts().entrySet()
                .stream()
                .sorted(MapEntryComparator.INSTANCE)
                .map(entry -> entry.getKey() + "티어 " + entry.getValue())
                .collect(Collectors.joining(", "));

        return team.roster().size() + "명"
                + (races.isBlank() ? "" : " / 종족 " + races)
                + (tiers.isBlank() ? "" : " / 티어 " + tiers);
    }

    private String availableCandidateSummary(DraftAiDraftContext context, TeamAiState currentTeam) {
        List<CandidateAiState> ranked = rankAvailableCandidates(
                context.availableCandidates(),
                currentTeam
        );

        if (ranked.isEmpty()) {
            return "남은 후보가 없습니다.";
        }

        return ranked.stream()
                .limit(AVAILABLE_CANDIDATE_SUMMARY_LIMIT)
                .map(candidate -> displayCandidateName(candidate)
                        + " (티어 " + valueOrUnknown(candidate.tier())
                        + ", 종족 " + valueOrUnknown(candidate.race()) + ")")
                .collect(Collectors.joining("\n"));
    }

    private int currentTeamRaceCount(TeamAiState currentTeam, String race) {
        if (currentTeam == null || race == null || race.isBlank()) {
            return 0;
        }

        return currentTeam.raceCounts().getOrDefault(race, 0);
    }

    private int parseTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String digits = tier.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException _) {
            return Integer.MAX_VALUE;
        }
    }

    private String displayCandidateName(DraftPickResponseDto pick) {
        return firstNonBlank(pick.getCandidateName(), pick.getCandidateUserLoginId(), idFallback(pick.getCandidateUserId()));
    }

    private String displayCandidateName(CandidateAiState candidate) {
        return firstNonBlank(candidate.candidateName(), idFallback(candidate.candidateUserId()));
    }

    private String displayCandidateName(RosterAiState rosterItem) {
        return firstNonBlank(rosterItem.candidateName(), idFallback(rosterItem.candidateUserId()));
    }

    private String valueOrUnknown(Object value) {
        if (value == null) {
            return "미상";
        }
        String text = String.valueOf(value);
        return text.isBlank() ? "미상" : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "미상";
    }

    private static String idFallback(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    record PickReviewContext(
            DraftAiDraftContext draftContext,
            DraftPickResponseDto justPicked,
            TeamAiState evaluatedTeam,
            Long expectedNextPickNo,
            Long expectedRecommendedTeamId
    ) {
    }

    record RecommendationContext(
            DraftAiDraftContext draftContext,
            DraftPickResponseDto justPicked,
            DraftLiveCurrentTurnResponseDto currentTurn,
            TeamAiState currentTeam,
            CandidateAiState recommendedCandidate
    ) {
    }

    private static final class DraftAiDraftContext {
        private final Long sessionId;
        private final Integer teamCount;
        private final LinkedHashMap<Long, TeamAiState> teams;
        private final LinkedHashMap<Long, CandidateAiState> availableCandidates;
        private final Instant createdAt;
        private Long lastAppliedPickNo;
        private Instant updatedAt;

        private DraftAiDraftContext(
                Long sessionId,
                Integer teamCount,
                LinkedHashMap<Long, TeamAiState> teams,
                LinkedHashMap<Long, CandidateAiState> availableCandidates,
                Long lastAppliedPickNo
        ) {
            this.sessionId = sessionId;
            this.teamCount = teamCount;
            this.teams = teams;
            this.availableCandidates = availableCandidates;
            this.lastAppliedPickNo = lastAppliedPickNo;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
        }

        private static DraftAiDraftContext fromSnapshot(Long sessionId, DraftLiveSnapshotResponseDto snapshot) {
            LinkedHashMap<Long, TeamAiState> teams = new LinkedHashMap<>();
            if (snapshot.getTeams() != null) {
                for (DraftLiveTeamResponseDto team : snapshot.getTeams()) {
                    if (team.getId() != null) {
                        teams.put(team.getId(), TeamAiState.from(team));
                    }
                }
            }

            LinkedHashMap<Long, CandidateAiState> availableCandidates = new LinkedHashMap<>();
            if (snapshot.getAvailableCandidates() != null) {
                for (DraftCandidateResponseDto candidate : snapshot.getAvailableCandidates()) {
                    if (candidate.getCandidateUserId() != null) {
                        availableCandidates.put(candidate.getCandidateUserId(), CandidateAiState.from(candidate));
                    }
                }
            }

            Integer teamCount = snapshot.getSession().getTeamCount();
            return new DraftAiDraftContext(
                    sessionId,
                    teamCount,
                    teams,
                    availableCandidates,
                    maxAppliedPickNo(snapshot)
            );
        }

        private boolean isCompatibleWith(DraftLiveSnapshotResponseDto snapshot) {
            if (snapshot.getSession() == null) {
                return false;
            }
            Long snapshotSessionId = snapshot.getSession().getId();
            if (snapshotSessionId != null && !Objects.equals(sessionId, snapshotSessionId)) {
                return false;
            }
            if (!Objects.equals(teamCount, snapshot.getSession().getTeamCount())) {
                return false;
            }
            if (snapshot.getTeams() == null) {
                return true;
            }
            return snapshot.getTeams().stream()
                    .map(DraftLiveTeamResponseDto::getId)
                    .filter(Objects::nonNull)
                    .allMatch(teams::containsKey);
        }

        private void applyLatestPick(DraftLiveSnapshotResponseDto snapshot) {
            if (snapshot.getRecentPicks() == null || snapshot.getRecentPicks().isEmpty()) {
                return;
            }
            applyPick(snapshot.getRecentPicks().get(0));
        }

        private void applyPick(DraftPickResponseDto pick) {
            if (pick == null || pick.getPickNo() == null) {
                return;
            }
            if (lastAppliedPickNo != null && pick.getPickNo() <= lastAppliedPickNo) {
                touch();
                return;
            }
            if (pick.getCandidateUserId() != null) {
                availableCandidates.remove(pick.getCandidateUserId());
            }

            if (pick.getDraftTeamId() != null) {
                TeamAiState team = teams.computeIfAbsent(
                        pick.getDraftTeamId(),
                        teamId -> new TeamAiState(teamId, pick.getDraftTeamName())
                );
                team.addRoster(RosterAiState.from(pick));
            }

            lastAppliedPickNo = pick.getPickNo();
            touch();
        }

        private Optional<TeamAiState> findTeam(Long teamId) {
            return Optional.ofNullable(teams.get(teamId));
        }

        private DraftAiDraftContext copy() {
            LinkedHashMap<Long, TeamAiState> teamCopies = new LinkedHashMap<>();
            teams.forEach((teamId, team) -> teamCopies.put(teamId, team.copy()));

            LinkedHashMap<Long, CandidateAiState> candidateCopies = new LinkedHashMap<>(availableCandidates);
            return new DraftAiDraftContext(
                    sessionId,
                    teamCount,
                    teamCopies,
                    candidateCopies,
                    lastAppliedPickNo
            );
        }

        private Collection<TeamAiState> teams() {
            return teams.values();
        }

        private Collection<CandidateAiState> availableCandidates() {
            return availableCandidates.values();
        }

        private Long lastAppliedPickNo() {
            return lastAppliedPickNo;
        }

        private int availableCandidateCount() {
            return availableCandidates.size();
        }

        private boolean isExpired(Instant now) {
            return updatedAt.plus(CONTEXT_TTL).isBefore(now);
        }

        private void touch() {
            updatedAt = Instant.now();
        }

        private static Long maxAppliedPickNo(DraftLiveSnapshotResponseDto snapshot) {
            long maxPickNo = 0L;
            boolean found = false;

            if (snapshot.getTeams() != null) {
                for (DraftLiveTeamResponseDto team : snapshot.getTeams()) {
                    if (team.getRoster() == null) {
                        continue;
                    }
                    for (DraftLiveRosterItemResponseDto item : team.getRoster()) {
                        if (item.getPickNo() != null && item.getPickNo() > maxPickNo) {
                            maxPickNo = item.getPickNo();
                            found = true;
                        }
                    }
                }
            }

            if (snapshot.getRecentPicks() != null) {
                for (DraftPickResponseDto pick : snapshot.getRecentPicks()) {
                    if (pick.getPickNo() != null && pick.getPickNo() > maxPickNo) {
                        maxPickNo = pick.getPickNo();
                        found = true;
                    }
                }
            }

            return found ? maxPickNo : 0L;
        }
    }

    private static final class TeamAiState {
        private final Long teamId;
        private final String teamName;
        private final List<RosterAiState> roster = new ArrayList<>();
        private final LinkedHashMap<String, Integer> raceCounts = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> tierCounts = new LinkedHashMap<>();

        private TeamAiState(Long teamId, String teamName) {
            this.teamId = teamId;
            this.teamName = teamName;
        }

        private static TeamAiState from(DraftLiveTeamResponseDto team) {
            TeamAiState state = new TeamAiState(team.getId(), team.getTeamName());
            if (team.getRoster() != null) {
                for (DraftLiveRosterItemResponseDto rosterItem : team.getRoster()) {
                    state.addRoster(RosterAiState.from(rosterItem));
                }
            }
            return state;
        }

        private void addRoster(RosterAiState item) {
            boolean duplicate = roster.stream()
                    .anyMatch(existing -> (existing.pickNo() != null
                            && item.pickNo() != null
                            && Objects.equals(existing.pickNo(), item.pickNo()))
                            || (existing.candidateUserId() != null
                            && item.candidateUserId() != null
                            && Objects.equals(existing.candidateUserId(), item.candidateUserId())));
            if (duplicate) {
                return;
            }

            roster.add(item);
            increment(raceCounts, item.race());
            increment(tierCounts, item.tier());
        }

        private TeamAiState copy() {
            TeamAiState copy = new TeamAiState(teamId, teamName);
            copy.roster.addAll(roster);
            copy.raceCounts.putAll(raceCounts);
            copy.tierCounts.putAll(tierCounts);
            return copy;
        }

        private String teamName() {
            return teamName;
        }

        private List<RosterAiState> roster() {
            return roster;
        }

        private Map<String, Integer> raceCounts() {
            return raceCounts;
        }

        private Map<String, Integer> tierCounts() {
            return tierCounts;
        }

        private void increment(Map<String, Integer> counts, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            counts.merge(value, 1, Integer::sum);
        }
    }

    private record CandidateAiState(
            Long candidateUserId,
            String candidateName,
            String tier,
            String race
    ) {
        private static CandidateAiState from(DraftCandidateResponseDto candidate) {
            return new CandidateAiState(
                    candidate.getCandidateUserId(),
                    firstNonBlank(
                            candidate.getCandidateName(),
                            candidate.getCandidateUserLoginId(),
                            idFallback(candidate.getCandidateUserId())
                    ),
                    candidate.getTier(),
                    candidate.getRace()
            );
        }
    }

    private record RosterAiState(
            Long pickNo,
            Long candidateUserId,
            String candidateName,
            String tier,
            String race
    ) {
        private static RosterAiState from(DraftLiveRosterItemResponseDto rosterItem) {
            return new RosterAiState(
                    rosterItem.getPickNo(),
                    rosterItem.getCandidateUserId(),
                    firstNonBlank(
                            rosterItem.getCandidateName(),
                            rosterItem.getCandidateUserLoginId(),
                            idFallback(rosterItem.getCandidateUserId())
                    ),
                    rosterItem.getTier(),
                    rosterItem.getRace()
            );
        }

        private static RosterAiState from(DraftPickResponseDto pick) {
            return new RosterAiState(
                    pick.getPickNo(),
                    pick.getCandidateUserId(),
                    firstNonBlank(
                            pick.getCandidateName(),
                            pick.getCandidateUserLoginId(),
                            idFallback(pick.getCandidateUserId())
                    ),
                    pick.getTier(),
                    pick.getRace()
            );
        }
    }

    private enum MapEntryComparator implements Comparator<Map.Entry<String, Integer>> {
        INSTANCE;

        @Override
        public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
            return left.getKey().compareToIgnoreCase(right.getKey());
        }
    }
}
