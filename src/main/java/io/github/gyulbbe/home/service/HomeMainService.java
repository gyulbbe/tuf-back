package io.github.gyulbbe.home.service;

import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.repository.BoardRepository;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.home.dto.HomeMainBotAlertResponse;
import io.github.gyulbbe.home.dto.HomeMainGalleryPostResponse;
import io.github.gyulbbe.home.dto.HomeMainOngoingResponse;
import io.github.gyulbbe.home.dto.HomeMainResponse;
import io.github.gyulbbe.home.dto.HomeScheduleResponse;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeMainService {

    private static final int ONGOING_SOURCE_LIMIT = 4;
    private static final int ONGOING_LIMIT = 4;
    private static final int BOT_ALERT_LIMIT = 3;
    private static final int SCHEDULE_LIMIT = 4;
    private static final String TYPE_DRAFT = "DRAFT";
    private static final String TYPE_TOURNAMENT = "TOURNAMENT";
    private static final String TYPE_SCHEDULE = "SCHEDULE";
    private static final String TYPE_GALLERY = "GALLERY";
    private static final String TYPE_DEFAULT = "DEFAULT";
    private static final String GROUP_NOTICE = "NOTICE";
    private static final String GROUP_PROLEAGUE = "PROLEAGUE";
    private static final String GROUP_PERSONAL_LEAGUE = "PERSONAL_LEAGUE";
    private static final String GROUP_PERSONAL = "PERSONAL";
    private static final List<String> DRAFT_ONGOING_STATUSES = List.of("LIVE", "PAUSED");

    private final HomeScheduleService homeScheduleService;
    private final DraftSessionRepository draftSessionRepository;
    private final DraftCandidateRepository draftCandidateRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentStageRepository tournamentStageRepository;
    private final TournamentGroupRepository tournamentGroupRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;
    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    public ResponseDto<HomeMainResponse> getHomeMain() {
        try {
            List<HomeScheduleResponse> schedules = safeLoad("schedules", () -> homeScheduleService.findPublicSchedules(SCHEDULE_LIMIT));
            List<HomeMainOngoingResponse> ongoing = safeLoad("ongoing", this::loadOngoing);
            List<HomeMainGalleryPostResponse> galleryPosts = safeLoad("galleryPosts", this::loadGalleryPosts);
            List<HomeMainBotAlertResponse> botAlerts = safeLoad(
                    "botAlerts",
                    () -> buildBotAlerts(schedules, ongoing, galleryPosts)
            );

            return ResponseDto.success(HomeMainResponse.builder()
                    .notice(resolveNotice(schedules))
                    .proleagueSchedules(resolveProleagueSchedules(schedules))
                    .personalLeagueSchedules(resolvePersonalLeagueSchedules(schedules))
                    .ongoing(ongoing)
                    .botAlerts(botAlerts)
                    .galleryPosts(galleryPosts)
                    .schedules(schedules)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to load home main.", e);
            return ResponseDto.fail("Failed to load home main.");
        }
    }

    private HomeScheduleResponse resolveNotice(List<HomeScheduleResponse> schedules) {
        return schedules.stream()
                .filter(schedule -> GROUP_NOTICE.equals(schedule.getScheduleGroup()))
                .findFirst()
                .orElse(null);
    }

    private List<HomeScheduleResponse> resolveProleagueSchedules(List<HomeScheduleResponse> schedules) {
        return schedules.stream()
                .filter(schedule -> GROUP_PROLEAGUE.equals(schedule.getScheduleGroup()))
                .toList();
    }

    private List<HomeScheduleResponse> resolvePersonalLeagueSchedules(List<HomeScheduleResponse> schedules) {
        return schedules.stream()
                .filter(schedule -> GROUP_PERSONAL_LEAGUE.equals(schedule.getScheduleGroup())
                        || GROUP_PERSONAL.equals(schedule.getScheduleGroup()))
                .toList();
    }

    private List<HomeMainOngoingResponse> loadOngoing() {
        return Stream.concat(loadDraftOngoing().stream(), loadTournamentOngoing().stream())
                .sorted(Comparator
                        .comparing(
                                HomeMainOngoingResponse::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(
                                HomeMainOngoingResponse::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ))
                .limit(ONGOING_LIMIT)
                .toList();
    }

    private List<HomeMainOngoingResponse> loadDraftOngoing() {
        return draftSessionRepository.findHomeMainOngoingSessions(
                        DRAFT_ONGOING_STATUSES,
                        PageRequest.of(0, ONGOING_SOURCE_LIMIT)
                )
                .stream()
                .map(this::toDraftOngoing)
                .toList();
    }

    private HomeMainOngoingResponse toDraftOngoing(DraftSessionEntity draft) {
        return HomeMainOngoingResponse.builder()
                .type(TYPE_DRAFT)
                .id(draft.getId())
                .title(draft.getTitle())
                .status(draft.getStatus())
                .primaryText(resolveDraftPrimaryText(draft))
                .secondaryText(resolveDraftSecondaryText(draft))
                .updatedAt(resolveUpdatedAt(draft.getUpdateDate(), draft.getRegDate()))
                .build();
    }

    private String resolveDraftPrimaryText(DraftSessionEntity draft) {
        if ("PAUSED".equals(draft.getStatus())) {
            return "일시정지";
        }
        if (draft.getCurrentPickNo() != null) {
            return "현재 " + draft.getCurrentPickNo() + "픽 진행 중";
        }
        return "드래프트 진행 중";
    }

    private String resolveDraftSecondaryText(DraftSessionEntity draft) {
        Long candidateCount = safeCandidateCount(draft.getId());
        if (draft.getTeamCount() != null && candidateCount != null) {
            return draft.getTeamCount() + "팀 · " + candidateCount + "명";
        }
        if (draft.getTeamCount() != null) {
            return draft.getTeamCount() + "팀";
        }
        if (candidateCount != null) {
            return candidateCount + "명";
        }
        return null;
    }

    private Long safeCandidateCount(Long draftSessionId) {
        try {
            return draftSessionId == null ? null : draftCandidateRepository.countByDraftSessionId(draftSessionId);
        } catch (Exception e) {
            log.warn("Failed to count draft candidates. draftSessionId={}", draftSessionId, e);
            return null;
        }
    }

    private List<HomeMainOngoingResponse> loadTournamentOngoing() {
        return tournamentRepository.findHomeMainLiveTournaments(
                        TournamentEntity.STATUS_LIVE,
                        PageRequest.of(0, ONGOING_SOURCE_LIMIT)
                )
                .stream()
                .map(this::toTournamentOngoing)
                .toList();
    }

    private HomeMainOngoingResponse toTournamentOngoing(TournamentEntity tournament) {
        List<TournamentStageEntity> stages = tournamentStageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(tournament.getId());
        List<Long> stageIds = stages.stream()
                .map(TournamentStageEntity::getId)
                .toList();
        long groupCount = stageIds.isEmpty() ? 0L : tournamentGroupRepository.countByStageIdIn(stageIds);
        long participantCount = tournamentParticipantRepository.countByTournamentId(tournament.getId());

        return HomeMainOngoingResponse.builder()
                .type(TYPE_TOURNAMENT)
                .id(tournament.getId())
                .title(tournament.getTitle())
                .status(tournament.getStatus())
                .primaryText(resolveTournamentPrimaryText(stages))
                .secondaryText(participantCount + "명 · " + groupCount + "개 조")
                .updatedAt(resolveUpdatedAt(tournament.getUpdateDate(), tournament.getRegDate()))
                .build();
    }

    private String resolveTournamentPrimaryText(List<TournamentStageEntity> stages) {
        if (stages == null || stages.isEmpty()) {
            return "토너먼트 진행 중";
        }

        String stageType = stages.get(0).getStageType();
        if (TournamentStageEntity.TYPE_SINGLE_ELIMINATION.equals(stageType)) {
            return "싱글 엘리미네이션";
        }
        if (TournamentStageEntity.TYPE_DUAL_GROUP.equals(stageType)) {
            return "듀얼 조별전";
        }
        return "토너먼트 진행 중";
    }

    private List<HomeMainGalleryPostResponse> loadGalleryPosts() {
        List<BoardEntity> boards = boardRepository.findTop5ByOrderByRegDateDescIdDesc();
        Map<Long, String> loginIdsByUserPk = loadLoginIds(boards.stream()
                .map(BoardEntity::getUserId)
                .filter(Objects::nonNull)
                .toList());

        return boards.stream()
                .map(board -> HomeMainGalleryPostResponse.builder()
                        .id(board.getId())
                        .title(board.getTitle())
                        .summaryText(createSummaryText(board.getText()))
                        .authorUserId(board.getUserId() == null ? null : loginIdsByUserPk.get(board.getUserId()))
                        .regDate(board.getRegDate())
                        .build())
                .toList();
    }

    private Map<Long, String> loadLoginIds(List<Long> userIds) {
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(userIds));
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(distinctIds).stream()
                .filter(user -> user.getUserId() != null && !user.getUserId().isBlank())
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getUserId, (left, right) -> left));
    }

    private String createSummaryText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private List<HomeMainBotAlertResponse> buildBotAlerts(
            List<HomeScheduleResponse> schedules,
            List<HomeMainOngoingResponse> ongoing,
            List<HomeMainGalleryPostResponse> galleryPosts
    ) {
        List<HomeMainBotAlertResponse> alerts = new ArrayList<>();

        if (!schedules.isEmpty()) {
            HomeScheduleResponse schedule = schedules.get(0);
            alerts.add(HomeMainBotAlertResponse.builder()
                    .type(TYPE_SCHEDULE)
                    .message(scheduleMessage(schedule))
                    .sourceId(schedule.getId())
                    .build());
        }

        ongoing.stream()
                .filter(item -> TYPE_TOURNAMENT.equals(item.getType()))
                .findFirst()
                .ifPresent(tournament -> alerts.add(HomeMainBotAlertResponse.builder()
                        .type(TYPE_TOURNAMENT)
                        .message("현재 " + tournament.getTitle() + "가 진행 중입니다.")
                        .sourceId(tournament.getId())
                        .build()));

        ongoing.stream()
                .filter(item -> TYPE_DRAFT.equals(item.getType()))
                .findFirst()
                .ifPresent(draft -> alerts.add(HomeMainBotAlertResponse.builder()
                        .type(TYPE_DRAFT)
                        .message(draft.getTitle() + "가 진행 중입니다.")
                        .sourceId(draft.getId())
                        .build()));

        if (!galleryPosts.isEmpty()) {
            HomeMainGalleryPostResponse post = galleryPosts.get(0);
            alerts.add(HomeMainBotAlertResponse.builder()
                    .type(TYPE_GALLERY)
                    .message("최근 갤러리에 새 글이 올라왔습니다.")
                    .sourceId(post.getId())
                    .build());
        }

        if (alerts.isEmpty()) {
            alerts.add(HomeMainBotAlertResponse.builder()
                    .type(TYPE_DEFAULT)
                    .message("오늘 등록된 주요 일정은 없습니다.")
                    .sourceId(null)
                    .build());
        }

        return alerts.stream()
                .limit(BOT_ALERT_LIMIT)
                .toList();
    }

    private String scheduleMessage(HomeScheduleResponse schedule) {
        if (schedule.getScheduledAt() != null && schedule.getScheduledAt().toLocalDate().equals(LocalDate.now())) {
            return "오늘의 메인 일정은 " + schedule.getTitle() + "입니다.";
        }
        return "가까운 메인 일정은 " + schedule.getTitle() + "입니다.";
    }

    private LocalDateTime resolveUpdatedAt(LocalDateTime updateDate, LocalDateTime regDate) {
        return updateDate == null ? regDate : updateDate;
    }

    private <T> List<T> safeLoad(String section, Supplier<List<T>> supplier) {
        try {
            List<T> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            log.warn("Failed to load home main section. section={}", section, e);
            return List.of();
        }
    }
}
