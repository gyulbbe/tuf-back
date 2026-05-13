package io.github.gyulbbe.persistence;

import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.entity.BoardCommentEntity;
import io.github.gyulbbe.board.repository.BoardCommentRepository;
import io.github.gyulbbe.board.repository.BoardRepository;
import io.github.gyulbbe.commentary.entity.CommentaryEntity;
import io.github.gyulbbe.commentary.repository.CommentaryRepository;
import io.github.gyulbbe.common.utils.trace.TraceEntity;
import io.github.gyulbbe.common.utils.trace.TraceRepository;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.repository.LeagueParticipationRepository;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.map.repository.MapRepository;
import io.github.gyulbbe.match.entity.MatchInfoEntity;
import io.github.gyulbbe.match.repository.MatchInfoRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan(basePackageClasses = {
        BoardEntity.class,
        BoardCommentEntity.class,
        CommentaryEntity.class,
        TraceEntity.class,
        DraftSessionEntity.class,
        DraftTeamEntity.class,
        LeagueEntity.class,
        LeagueParticipationEntity.class,
        ProleagueTeamEntity.class,
        MapEntity.class,
        MatchInfoEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        BoardRepository.class,
        BoardCommentRepository.class,
        CommentaryRepository.class,
        TraceRepository.class,
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        LeagueRepository.class,
        LeagueParticipationRepository.class,
        ProleagueTeamRepository.class,
        MapRepository.class,
        MatchInfoRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sequencepersistence;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(QueryDslConfig.class)
class SequencePersistenceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private LeagueParticipationRepository leagueParticipationRepository;

    @Autowired
    private ProleagueTeamRepository proleagueTeamRepository;

    @Autowired
    private MapRepository mapRepository;

    @Autowired
    private MatchInfoRepository matchInfoRepository;

    @Autowired
    private CommentaryRepository commentaryRepository;

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private BoardCommentRepository boardCommentRepository;

    @Autowired
    private TraceRepository traceRepository;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftTeamRepository draftTeamRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void sequenceBackedRepositoriesGenerateIdsOnSave() {
        UserEntity leader = userRepository.save(newUser("leader01", "Leader 01"));
        UserEntity viceLeader = userRepository.save(newUser("leader02", "Leader 02"));
        LeagueEntity league = leagueRepository.save(LeagueEntity.builder()
                .leagueName("Spring League")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .build());
        MapEntity map = mapRepository.save(MapEntity.builder()
                .mapName("Polypoid")
                .image("polypoid.png")
                .build());
        MatchInfoEntity match = matchInfoRepository.save(MatchInfoEntity.builder()
                .leagueId(league.getId())
                .matchType("PROLEAGUE")
                .format("1V1")
                .winner("leader01")
                .loser("leader02")
                .sets("2:1")
                .build());
        CommentaryEntity commentary = commentaryRepository.save(CommentaryEntity.builder()
                .matchInfoId(match.getId())
                .matchSummary("Fast expansion into late-game victory")
                .build());
        BoardEntity board = boardRepository.save(BoardEntity.builder()
                .userId(leader.getId())
                .authorName("Leader 01")
                .title("Sequence migration note")
                .text("Board save should assign a sequence id")
                .build());
        BoardCommentEntity comment = boardCommentRepository.save(BoardCommentEntity.builder()
                .boardId(board.getId())
                .userId(leader.getId())
                .authorName("Leader 01")
                .parentId(null)
                .depth(0)
                .content("First board comment")
                .build());
        TraceEntity trace = traceRepository.save(TraceEntity.builder()
                .userId(leader.getId())
                .type("LOGIN")
                .text("Sequence persistence smoke test")
                .createdDatetime(LocalDateTime.now())
                .build());
        LeagueParticipationEntity participation = leagueParticipationRepository.save(LeagueParticipationEntity.builder()
                .leagueId(league.getId())
                .userId(leader.getId())
                .race("ZERG")
                .build());
        ProleagueTeamEntity proleagueTeam = proleagueTeamRepository.save(ProleagueTeamEntity.builder()
                .teamName("Alpha")
                .leagueId(league.getId())
                .leaderId(leader.getId())
                .viceLeaderId(viceLeader.getId())
                .build());
        DraftSessionEntity session = draftSessionRepository.save(DraftSessionEntity.builder()
                .title("Season Opening Draft")
                .ownerUserId(leader.getId())
                .status("READY")
                .orderMode("BASIC")
                .teamCount(2)
                .pickTimeSeconds(60)
                .currentPickNo(1)
                .build());
        DraftTeamEntity draftTeam = draftTeamRepository.save(DraftTeamEntity.builder()
                .draftSessionId(session.getId())
                .teamName("Red")
                .displayOrder(1)
                .build());

        entityManager.flush();
        entityManager.clear();

        assertThat(leader.getId()).isNotNull().isPositive();
        assertThat(viceLeader.getId()).isNotNull().isPositive();
        assertThat(league.getId()).isNotNull().isPositive();
        assertThat(map.getId()).isNotNull().isPositive();
        assertThat(match.getId()).isNotNull().isPositive();
        assertThat(commentary.getId()).isNotNull().isPositive();
        assertThat(board.getId()).isNotNull().isPositive();
        assertThat(comment.getId()).isNotNull().isPositive();
        assertThat(trace.getId()).isNotNull().isPositive();
        assertThat(participation.getId()).isNotNull().isPositive();
        assertThat(proleagueTeam.getId()).isNotNull().isPositive();
        assertThat(session.getId()).isNotNull().isPositive();
        assertThat(draftTeam.getId()).isNotNull().isPositive();
    }

    @Test
    void sequenceBackedRepositoriesGenerateDistinctIdsOnBulkSave() {
        List<UserEntity> savedUsers = userRepository.saveAll(List.of(
                newUser("bulk01", "Bulk 01"),
                newUser("bulk02", "Bulk 02"),
                newUser("bulk03", "Bulk 03")
        ));

        DraftSessionEntity firstSession = draftSessionRepository.save(DraftSessionEntity.builder()
                .title("Draft A")
                .ownerUserId(savedUsers.get(0).getId())
                .status("READY")
                .orderMode("BASIC")
                .teamCount(2)
                .pickTimeSeconds(30)
                .currentPickNo(1)
                .build());
        DraftSessionEntity secondSession = draftSessionRepository.save(DraftSessionEntity.builder()
                .title("Draft B")
                .ownerUserId(savedUsers.get(1).getId())
                .status("READY")
                .orderMode("BASIC")
                .teamCount(3)
                .pickTimeSeconds(45)
                .currentPickNo(1)
                .build());

        entityManager.flush();

        assertThat(savedUsers)
                .extracting(UserEntity::getId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        assertThat(savedUsers.get(1).getId()).isGreaterThan(savedUsers.get(0).getId());
        assertThat(savedUsers.get(2).getId()).isGreaterThan(savedUsers.get(1).getId());

        assertThat(firstSession.getId()).isNotNull().isPositive();
        assertThat(secondSession.getId()).isNotNull().isPositive();
        assertThat(secondSession.getId()).isGreaterThan(firstSession.getId());
    }

    private UserEntity newUser(String userId, String name) {
        return UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status("ACTIVE")
                .userType("ROLE_USER")
                .coin(1000L)
                .build();
    }
}
