package io.github.gyulbbe.persistence;

import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.entity.BoardCommentEntity;
import io.github.gyulbbe.commentary.entity.CommentaryEntity;
import io.github.gyulbbe.common.utils.trace.TraceEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.map.entity.MapEntity;
import io.github.gyulbbe.match.entity.MatchInfoEntity;
import io.github.gyulbbe.speech.entity.SpeechEntity;
import io.github.gyulbbe.user.entity.UserEntity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceStrategyAnnotationTest {

    @ParameterizedTest
    @MethodSource("sequenceEntities")
    void sequenceBackedEntitiesUseExpectedSequenceGenerator(
            Class<?> entityClass,
            String generatorName,
            String sequenceName
    ) throws NoSuchFieldException {
        Field idField = entityClass.getDeclaredField("id");
        GeneratedValue generatedValue = idField.getAnnotation(GeneratedValue.class);
        SequenceGenerator sequenceGenerator = entityClass.getAnnotation(SequenceGenerator.class);

        assertThat(generatedValue).isNotNull();
        assertThat(generatedValue.strategy()).isEqualTo(GenerationType.SEQUENCE);
        assertThat(generatedValue.generator()).isEqualTo(generatorName);

        assertThat(sequenceGenerator).isNotNull();
        assertThat(sequenceGenerator.name()).isEqualTo(generatorName);
        assertThat(sequenceGenerator.sequenceName()).isEqualTo(sequenceName);
        assertThat(sequenceGenerator.allocationSize()).isEqualTo(1);
    }

    private static Stream<Arguments> sequenceEntities() {
        return Stream.of(
                Arguments.of(BoardEntity.class, "boards_seq_gen", "BOARDS_SEQ"),
                Arguments.of(BoardCommentEntity.class, "comments_seq_gen", "COMMENTS_SEQ"),
                Arguments.of(CommentaryEntity.class, "commentaries_seq_gen", "COMMENTARIES_SEQ"),
                Arguments.of(TraceEntity.class, "traces_seq_gen", "TRACES_SEQ"),
                Arguments.of(DraftSessionEntity.class, "draft_sessions_seq_gen", "DRAFT_SESSIONS_SEQ"),
                Arguments.of(DraftTeamEntity.class, "draft_teams_seq_gen", "DRAFT_TEAMS_SEQ"),
                Arguments.of(LeagueEntity.class, "leagues_seq_gen", "LEAGUES_SEQ"),
                Arguments.of(LeagueParticipationEntity.class, "league_participations_seq_gen", "LEAGUE_PARTICIPATIONS_SEQ"),
                Arguments.of(ProleagueTeamEntity.class, "proleague_teams_seq_gen", "PROLEAGUE_TEAMS_SEQ"),
                Arguments.of(MapEntity.class, "maps_seq_gen", "MAPS_SEQ"),
                Arguments.of(MatchInfoEntity.class, "match_infos_seq_gen", "MATCH_INFOS_SEQ"),
                Arguments.of(SpeechEntity.class, "speeches_seq_gen", "SPEECHES_SEQ"),
                Arguments.of(UserEntity.class, "users_seq_gen", "USERS_SEQ")
        );
    }
}
