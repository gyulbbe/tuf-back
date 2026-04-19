package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.config.QueryDslConfig;
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

@DataJpaTest
@Import({
        QueryDslConfig.class,
        DraftQueryRepositoryImpl.class,
        DraftLiveSessionTracker.class,
        DraftService.class,
        DraftPermissionService.class,
        DraftSnapshotService.class,
        DraftEventPublisher.class,
        DraftLiveCommandService.class,
        DraftTimeoutScheduler.class
})
@EntityScan(basePackageClasses = {
        DraftSessionEntity.class,
        DraftTeamEntity.class,
        DraftCandidateEntity.class,
        DraftOrderEntity.class,
        DraftPickEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftCandidateRepository.class,
        DraftOrderRepository.class,
        DraftPickRepository.class,
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

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftTimeoutScheduler draftTimeoutScheduler;

    @Autowired
    private DraftLiveSessionTracker draftLiveSessionTracker;

    @Test
    void deadlineAt이_지난_live_세션은_자동으로_스킵된다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        Long teamBId = createTeam(sessionId, "B", 2);
        createOrder(sessionId, 1L, 1, teamAId);
        createOrder(sessionId, 2L, 1, teamBId);

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
    void paused_세션은_자동처리_대상이_아니다() {
        Long sessionId = createSession();
        Long teamAId = createTeam(sessionId, "A", 1);
        createOrder(sessionId, 1L, 1, teamAId);

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
        return draftService.createSession(requestDto).getData().getId();
    }

    private Long createTeam(Long sessionId, String teamName, int displayOrder) {
        DraftTeamRequestDto requestDto = new DraftTeamRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setTeamName(teamName);
        requestDto.setDisplayOrder(displayOrder);
        return draftService.createTeam(requestDto).getData().getId();
    }

    private void createOrder(Long sessionId, Long pickNo, int roundNo, Long teamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setRoundNo(roundNo);
        requestDto.setDraftTeamId(teamId);
        draftService.createOrder(requestDto);
    }
}
