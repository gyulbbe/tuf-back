package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.entity.DraftOrderEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftOrderRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import(DraftOrderPatternService.class)
@EntityScan(basePackageClasses = {
        DraftSessionEntity.class,
        DraftOrderEntity.class,
        DraftTeamEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftOrderRepository.class,
        DraftTeamRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:draftorderpatterndb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftOrderPatternServiceTest {

    @Autowired
    private DraftOrderPatternService draftOrderPatternService;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftOrderRepository draftOrderRepository;

    @Autowired
    private DraftTeamRepository draftTeamRepository;

    @Test
    void extends_two_team_snake_when_prefix_ends_mid_cycle() {
        Long sessionId = saveSession("SNAKE", 2);
        List<Long> teams = saveTeams(sessionId, 2);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        saveOrders(sessionId, teamAId, teamBId, teamBId, teamAId, teamAId);

        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 6L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 7L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 8L).getDraftTeamId()).isEqualTo(teamAId);

        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamBId),
                tuple(4L, teamAId),
                tuple(5L, teamAId),
                tuple(6L, teamBId),
                tuple(7L, teamBId),
                tuple(8L, teamAId)
        );
    }

    @Test
    void extends_two_team_snake_full_cycle() {
        Long sessionId = saveSession("SNAKE", 2);
        List<Long> teams = saveTeams(sessionId, 2);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        saveOrders(sessionId, teamAId, teamBId, teamBId, teamAId);

        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 5L).getDraftTeamId()).isEqualTo(teamAId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 6L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 7L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 8L).getDraftTeamId()).isEqualTo(teamAId);

        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamBId),
                tuple(4L, teamAId),
                tuple(5L, teamAId),
                tuple(6L, teamBId),
                tuple(7L, teamBId),
                tuple(8L, teamAId)
        );
    }

    @Test
    void extends_three_team_snake_full_cycle() {
        Long sessionId = saveSession("SNAKE", 3);
        List<Long> teams = saveTeams(sessionId, 3);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        Long teamCId = teams.get(2);
        saveOrders(sessionId, teamAId, teamBId, teamCId, teamCId, teamBId, teamAId);

        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 7L).getDraftTeamId()).isEqualTo(teamAId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 8L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 9L).getDraftTeamId()).isEqualTo(teamCId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 10L).getDraftTeamId()).isEqualTo(teamCId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 11L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 12L).getDraftTeamId()).isEqualTo(teamAId);

        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamCId),
                tuple(4L, teamCId),
                tuple(5L, teamBId),
                tuple(6L, teamAId),
                tuple(7L, teamAId),
                tuple(8L, teamBId),
                tuple(9L, teamCId),
                tuple(10L, teamCId),
                tuple(11L, teamBId),
                tuple(12L, teamAId)
        );
    }

    @Test
    void extends_three_team_basic_pattern() {
        Long sessionId = saveSession("BASIC", 3);
        List<Long> teams = saveTeams(sessionId, 3);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        Long teamCId = teams.get(2);
        saveOrders(sessionId, teamAId, teamBId, teamCId);

        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 4L).getDraftTeamId()).isEqualTo(teamAId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 5L).getDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderPatternService.getOrCreateOrder(sessionId, 6L).getDraftTeamId()).isEqualTo(teamCId);

        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamCId),
                tuple(4L, teamAId),
                tuple(5L, teamBId),
                tuple(6L, teamCId)
        );
    }

    @Test
    void declared_snake_mode_controls_ambiguous_two_pick_prefix() {
        Long sessionId = saveSession("SNAKE", 2);
        List<Long> teams = saveTeams(sessionId, 2);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        saveOrders(sessionId, teamAId, teamBId);

        DraftOrderEntity generated = draftOrderPatternService.getOrCreateOrder(sessionId, 3L);

        assertThat(generated.getDraftTeamId()).isEqualTo(teamBId);
        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamBId)
        );
    }

    @Test
    void declared_basic_mode_controls_ambiguous_two_pick_prefix() {
        Long sessionId = saveSession("BASIC", 2);
        List<Long> teams = saveTeams(sessionId, 2);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        saveOrders(sessionId, teamAId, teamBId);

        DraftOrderEntity generated = draftOrderPatternService.getOrCreateOrder(sessionId, 3L);

        assertThat(generated.getDraftTeamId()).isEqualTo(teamAId);
        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamAId)
        );
    }

    @Test
    void returns_existing_target_order_without_recalculating() {
        Long sessionId = saveSession("SNAKE", 2);
        List<Long> teams = saveTeams(sessionId, 2);
        Long teamAId = teams.get(0);
        Long teamBId = teams.get(1);
        saveOrders(sessionId, teamAId, teamBId, teamBId);

        DraftOrderEntity existing = draftOrderPatternService.getOrCreateOrder(sessionId, 3L);

        assertThat(existing.getDraftTeamId()).isEqualTo(teamBId);
        assertOrders(sessionId,
                tuple(1L, teamAId),
                tuple(2L, teamBId),
                tuple(3L, teamBId)
        );
    }

    private Long saveSession(String orderMode, int teamCount) {
        DraftSessionEntity session = draftSessionRepository.save(DraftSessionEntity.builder()
                .title("Draft Order Pattern " + orderMode)
                .status("READY")
                .orderMode(orderMode)
                .teamCount(teamCount)
                .pickTimeSeconds(30)
                .currentPickNo(1)
                .build());
        return session.getId();
    }

    private List<Long> saveTeams(Long sessionId, int teamCount) {
        List<Long> teamIds = new ArrayList<>(teamCount);
        for (int displayOrder = 1; displayOrder <= teamCount; displayOrder++) {
            DraftTeamEntity team = draftTeamRepository.save(DraftTeamEntity.builder()
                    .draftSessionId(sessionId)
                    .teamName("Team " + displayOrder + " for " + sessionId)
                    .displayOrder(displayOrder)
                    .build());
            teamIds.add(team.getId());
        }
        return teamIds;
    }

    private void saveOrders(Long sessionId, Long... draftTeamIds) {
        for (int index = 0; index < draftTeamIds.length; index++) {
            draftOrderRepository.save(DraftOrderEntity.builder()
                    .draftSessionId(sessionId)
                    .pickNo((long) index + 1L)
                    .draftTeamId(draftTeamIds[index])
                    .build());
        }
    }

    private void assertOrders(Long sessionId, org.assertj.core.groups.Tuple... orders) {
        assertThat(draftOrderRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId))
                .extracting(DraftOrderEntity::getPickNo, DraftOrderEntity::getDraftTeamId)
                .containsExactly(orders);
    }
}
