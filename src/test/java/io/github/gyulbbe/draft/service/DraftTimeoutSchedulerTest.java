package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamRequestDto;
import io.github.gyulbbe.draft.entity.DraftCandidateEntity;
import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftQueryRepositoryImpl;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamMemberEntity;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamMemberRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        DraftQueryRepositoryImpl.class,
        DraftLiveSessionTracker.class,
        DraftOrderPatternService.class,
        ProleagueDraftRosterSyncService.class,
        DraftService.class,
        DraftPermissionService.class,
        DraftSnapshotService.class,
        DraftEventPublisher.class,
        DraftLivePreviewRelayService.class,
        DraftLiveCommandService.class,
        DraftTimeoutScheduler.class
})
@EntityScan(basePackageClasses = {
        DraftSessionEntity.class,
        DraftTeamEntity.class,
        DraftCandidateEntity.class,
        DraftOrderEntity.class,
        DraftPickEntity.class,
        LeagueEntity.class,
        LeagueParticipationEntity.class,
        ProleagueTeamEntity.class,
        ProleagueTeamMemberEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftCandidateRepository.class,
        DraftOrderRepository.class,
        DraftPickRepository.class,
        LeagueRepository.class,
        ProleagueTeamRepository.class,
        ProleagueTeamMemberRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:drafttimeoutschedulerdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftTimeoutSchedulerTest {

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @MockitoBean
    private DraftAiAdviceService draftAiAdviceService;

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftCandidateRepository draftCandidateRepository;

    @Autowired
    private DraftTimeoutScheduler draftTimeoutScheduler;

    @Autowired
    private DraftLiveSessionTracker draftLiveSessionTracker;

    @Test
    void deadlineAt이_지난_live_세션은_자동으로_스킵된다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        Long teamBId = createTeam(sessionId, "B", 2);
        createCandidate(sessionId, 1001L);
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.start(teamAId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(5));
        draftLiveSessionTracker.synchronizeWithDatabase();

        draftTimeoutScheduler.processTimeouts();

        DraftSessionEntity updated = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updated.getCurrentPickNo()).isEqualTo(2);
        assertThat(updated.getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(updated.getStatus()).isEqualTo("LIVE");
    }

    @Test
    void overdue_live_session_without_waiting_candidates_finishes() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        createOrder(sessionId, 1L, teamAId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.start(teamAId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(5));
        draftLiveSessionTracker.synchronizeWithDatabase();

        draftTimeoutScheduler.processTimeouts();

        DraftSessionEntity updated = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("FINISHED");
        assertThat(updated.getCurrentPickNo()).isEqualTo(1);
        assertThat(updated.getCurrentDraftTeamId()).isNull();
        verify(draftAiAdviceService).evictContext(sessionId);
    }

    @Test
    void repeated_timeout_auto_skips_pause_session_after_two_full_rounds() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        Long teamBId = createTeam(sessionId, "B", 2);
        createCandidate(sessionId, 1001L);
        createOrder(sessionId, 1L, teamAId);
        createOrder(sessionId, 2L, teamBId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.start(teamAId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(5));
        draftLiveSessionTracker.synchronizeWithDatabase();

        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();
        assertLiveTurn(sessionId, 2, teamBId);

        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();
        assertLiveTurn(sessionId, 3, teamAId);

        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();
        assertLiveTurn(sessionId, 4, teamBId);

        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();

        DraftSessionEntity paused = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(paused.getStatus()).isEqualTo("PAUSED");
        assertThat(paused.getCurrentPickNo()).isEqualTo(5);
        assertThat(paused.getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(paused.getDeadlineAt()).isNull();
    }

    @Test
    void manual_turn_change_resets_consecutive_timeout_skip_guard() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        Long teamBId = createTeam(sessionId, "B", 2);
        createCandidate(sessionId, 1001L);
        for (long pickNo = 1L; pickNo <= 8L; pickNo++) {
            createOrder(sessionId, pickNo, pickNo % 2 == 1 ? teamAId : teamBId);
        }

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.start(teamAId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(5));
        draftLiveSessionTracker.synchronizeWithDatabase();

        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();
        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();
        expireSession(sessionId);
        draftTimeoutScheduler.processTimeouts();
        assertLiveTurn(sessionId, 4, teamBId);

        DraftSessionEntity manuallyAdvanced = draftSessionRepository.findById(sessionId).orElseThrow();
        manuallyAdvanced.advanceTurn(5, teamAId, LocalDateTime.now().minusSeconds(5));

        draftTimeoutScheduler.processTimeouts();

        DraftSessionEntity updated = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("LIVE");
        assertThat(updated.getCurrentPickNo()).isEqualTo(6);
        assertThat(updated.getCurrentDraftTeamId()).isEqualTo(teamBId);
    }

    @Test
    void timeout_skip_domain_failure_pauses_session() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        createCandidate(sessionId, 1001L);
        createOrder(sessionId, 1L, teamAId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.start(teamAId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(5));
        session.advanceTurn(99, null, LocalDateTime.now().minusSeconds(5));
        draftLiveSessionTracker.synchronizeWithDatabase();

        draftTimeoutScheduler.processTimeouts();

        DraftSessionEntity updated = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PAUSED");
        assertThat(updated.getCurrentPickNo()).isEqualTo(99);
        assertThat(updated.getDeadlineAt()).isNull();
    }

    @Test
    void paused_세션은_자동처리_대상이_아니다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        createOrder(sessionId, 1L, teamAId);

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.start(teamAId, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().minusSeconds(5));
        session.pause();

        draftTimeoutScheduler.processTimeouts();

        DraftSessionEntity updated = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("PAUSED");
        assertThat(updated.getCurrentPickNo()).isEqualTo(1);
    }

    private Long createSession() {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle("타임아웃 세션");
        requestDto.setStatus("READY");
        requestDto.setTeamCount(2);
        requestDto.setPickTimeSeconds(30);
        requestDto.setCurrentPickNo(1);
        return draftService.createSession(requestDto, adminActor()).getData().getId();
    }

    private Long createTeam(Long sessionId, String teamName, int displayOrder) {
        DraftTeamRequestDto requestDto = new DraftTeamRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setTeamName(teamName);
        requestDto.setDisplayOrder(displayOrder);
        return draftService.createTeam(requestDto, adminActor()).getData().getId();
    }

    private void createOrder(Long sessionId, Long pickNo, Long teamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(teamId);
        draftService.createOrder(requestDto, adminActor());
    }

    private void createCandidate(Long sessionId, Long candidateUserId) {
        draftCandidateRepository.save(DraftCandidateEntity.builder()
                .draftSessionId(sessionId)
                .candidateUserId(candidateUserId)
                .candidateName("Candidate " + candidateUserId)
                .race("ZERG")
                .status("WAITING")
                .build());
    }

    private void expireSession(Long sessionId) {
        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        session.extendDeadlineAt(LocalDateTime.now().minusSeconds(5));
    }

    private void assertLiveTurn(Long sessionId, int currentPickNo, Long currentDraftTeamId) {
        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        assertThat(session.getStatus()).isEqualTo("LIVE");
        assertThat(session.getCurrentPickNo()).isEqualTo(currentPickNo);
        assertThat(session.getCurrentDraftTeamId()).isEqualTo(currentDraftTeamId);
    }

    private AuthActor adminActor() {
        return new AuthActor(1L, "admin", "ROLE_ADMIN");
    }
}
