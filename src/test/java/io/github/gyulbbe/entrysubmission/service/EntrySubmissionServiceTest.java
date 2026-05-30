package io.github.gyulbbe.entrysubmission.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.entrysubmission.auth.EntrySubmissionActor;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionEntryRequestDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSessionCreateRequestDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSnapshotResponseDto;
import io.github.gyulbbe.entrysubmission.dto.EntrySubmissionSubmitRequestDto;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionEntryEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionPlayerEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionSessionEntity;
import io.github.gyulbbe.entrysubmission.entity.EntrySubmissionTeamEntity;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionEntryRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionPlayerRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionSessionRepository;
import io.github.gyulbbe.entrysubmission.repository.EntrySubmissionTeamRepository;
import io.github.gyulbbe.entrysubmission.ws.EntrySubmissionEventPublisher;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        EntrySubmissionPermissionService.class,
        EntrySubmissionSnapshotService.class,
        EntrySubmissionService.class,
        EntrySubmissionCommandService.class,
        EntrySubmissionEventPublisher.class
})
@EntityScan(basePackageClasses = {
        EntrySubmissionSessionEntity.class,
        EntrySubmissionTeamEntity.class,
        EntrySubmissionPlayerEntity.class,
        EntrySubmissionEntryEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        EntrySubmissionSessionRepository.class,
        EntrySubmissionTeamRepository.class,
        EntrySubmissionPlayerRepository.class,
        EntrySubmissionEntryRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:entrysubmissiondb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class EntrySubmissionServiceTest {

    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private EntrySubmissionService entrySubmissionService;

    @Autowired
    private EntrySubmissionCommandService entrySubmissionCommandService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createSession_includes_captains_and_calculates_default_set_count() {
        Long ownerId = createUser("entry-owner");
        Long team1CaptainId = createUser("captain-a");
        Long team2CaptainId = createUser("captain-b");

        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a", "b", "c"),
                List.of("e", "f", "g"),
                null
        );

        assertThat(snapshot.getSession().getStatus()).isEqualTo(EntrySubmissionSessionEntity.STATUS_SUBMITTING);
        assertThat(snapshot.getSession().getSetCount()).isEqualTo(4);
        assertThat(playerNames(snapshot, 1)).containsExactly("captain-a", "a", "b", "c");
        assertThat(playerNames(snapshot, 2)).containsExactly("captain-b", "e", "f", "g");
        assertThat(snapshot.getTeams()).extracting("submitted").containsExactly(false, false);
    }

    @Test
    void createSession_calculates_three_sets_for_six_players() {
        Long ownerId = createUser("entry-owner-six");
        Long team1CaptainId = createUser("captain-six-a");
        Long team2CaptainId = createUser("captain-six-b");

        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a", "b"),
                List.of("e", "f"),
                null
        );

        assertThat(snapshot.getSession().getSetCount()).isEqualTo(3);
        assertThat(playerNames(snapshot, 1)).containsExactly("captain-six-a", "a", "b");
        assertThat(playerNames(snapshot, 2)).containsExactly("captain-six-b", "e", "f");
    }

    @Test
    void createSession_rejects_blank_and_duplicate_player_names() {
        Long ownerId = createUser("entry-owner-invalid");
        Long team1CaptainId = createUser("captain-invalid-a");
        Long team2CaptainId = createUser("captain-invalid-b");

        ResponseDto<EntrySubmissionSnapshotResponseDto> blankResponse = entrySubmissionService.createSession(
                createRequest("blank", team1CaptainId, team2CaptainId, List.of(" "), List.of("ok"), null),
                actor(ownerId, "owner")
        );
        ResponseDto<EntrySubmissionSnapshotResponseDto> duplicateResponse = entrySubmissionService.createSession(
                createRequest("duplicate", team1CaptainId, team2CaptainId, List.of("Alpha", " alpha "), List.of("ok"), null),
                actor(ownerId, "owner")
        );

        assertThat(blankResponse.getMessage()).contains("blank");
        assertThat(duplicateResponse.getMessage()).contains("Duplicate player names");
    }

    @Test
    void createSession_allows_custom_set_count_greater_than_player_count() {
        Long ownerId = createUser("entry-owner-custom");
        Long team1CaptainId = createUser("captain-custom-a");
        Long team2CaptainId = createUser("captain-custom-b");

        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                5
        );

        assertThat(snapshot.getSession().getSetCount()).isEqualTo(5);
        assertThat(playerNames(snapshot, 1)).containsExactly("captain-custom-a", "a");
        assertThat(playerNames(snapshot, 2)).containsExactly("captain-custom-b", "b");
    }

    @Test
    void submitEntries_rejects_duplicate_players_when_set_count_does_not_exceed_team_player_count() {
        Long ownerId = createUser("entry-owner-no-repeat");
        Long team1CaptainId = createUser("captain-no-repeat-a");
        Long team2CaptainId = createUser("captain-no-repeat-b");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                null
        );
        Long team1CaptainPlayerId = playerId(snapshot, 1, "captain-no-repeat-a");

        assertThatThrownBy(() -> entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(team1CaptainPlayerId, team1CaptainPlayerId)),
                actor(team1CaptainId, "captain-no-repeat-a")
        )).hasMessageContaining("Duplicate players");
    }

    @Test
    void submitEntries_allows_duplicate_players_when_set_count_exceeds_team_player_count() {
        Long ownerId = createUser("entry-owner-repeat");
        Long team1CaptainId = createUser("captain-repeat-a");
        Long team2CaptainId = createUser("captain-repeat-b");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                3
        );
        Long team1CaptainPlayerId = playerId(snapshot, 1, "captain-repeat-a");

        EntrySubmissionSnapshotResponseDto submitted = entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(team1CaptainPlayerId, team1CaptainPlayerId, team1CaptainPlayerId)),
                actor(team1CaptainId, "captain-repeat-a")
        );

        assertThat(submitted.getTeams().get(0).isSubmitted()).isTrue();
        assertThat(submitted.getEntries()).hasSize(3);
        assertThat(submitted.getEntries()).extracting("playerId")
                .containsExactly(team1CaptainPlayerId, team1CaptainPlayerId, team1CaptainPlayerId);
    }

    @Test
    void only_team_captain_can_submit_own_entries() {
        Long ownerId = createUser("entry-owner-permission");
        Long team1CaptainId = createUser("captain-permission-a");
        Long team2CaptainId = createUser("captain-permission-b");
        Long strangerId = createUser("entry-stranger");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                null
        );
        Long team1CaptainPlayerId = playerId(snapshot, 1, "captain-permission-a");
        Long team2CaptainPlayerId = playerId(snapshot, 2, "captain-permission-b");

        assertThatThrownBy(() -> entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(team1CaptainPlayerId, team2CaptainPlayerId)),
                actor(strangerId, "stranger")
        )).hasMessageContaining("captain assigned");
    }

    @Test
    void one_team_submission_updates_snapshot_status() {
        Long ownerId = createUser("entry-owner-one");
        Long team1CaptainId = createUser("captain-one-a");
        Long team2CaptainId = createUser("captain-one-b");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                null
        );

        EntrySubmissionSnapshotResponseDto submitted = entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(
                        playerId(snapshot, 1, "captain-one-a"),
                        playerId(snapshot, 1, "a")
                )),
                actor(team1CaptainId, "captain-one-a")
        );

        assertThat(submitted.getSession().getStatus()).isEqualTo(EntrySubmissionSessionEntity.STATUS_SUBMITTING);
        assertThat(submitted.getTeams()).extracting("submitted").containsExactly(true, false);
        assertThat(submitted.getPermissions().isCanSubmit()).isFalse();
    }

    @Test
    void both_team_submissions_complete_session_and_build_matches() {
        Long ownerId = createUser("entry-owner-complete");
        Long team1CaptainId = createUser("captain-complete-a");
        Long team2CaptainId = createUser("captain-complete-b");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a", "b"),
                List.of("e", "f"),
                null
        );

        entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(
                        playerId(snapshot, 1, "captain-complete-a"),
                        playerId(snapshot, 1, "b"),
                        playerId(snapshot, 1, "a")
                )),
                actor(team1CaptainId, "captain-complete-a")
        );
        EntrySubmissionSnapshotResponseDto completed = entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(
                        playerId(snapshot, 2, "e"),
                        playerId(snapshot, 2, "captain-complete-b"),
                        playerId(snapshot, 2, "f")
                )),
                actor(team2CaptainId, "captain-complete-b")
        );

        assertThat(completed.getSession().getStatus()).isEqualTo(EntrySubmissionSessionEntity.STATUS_COMPLETED);
        assertThat(completed.getTeams()).extracting("submitted").containsExactly(true, true);
        assertThat(completed.getMatches()).extracting("team1PlayerName")
                .containsExactly("captain-complete-a", "b", "a");
        assertThat(completed.getMatches()).extracting("team2PlayerName")
                .containsExactly("e", "captain-complete-b", "f");
    }

    @Test
    void restartSession_clears_submissions_and_keeps_initial_setup() {
        Long ownerId = createUser("entry-owner-restart");
        Long team1CaptainId = createUser("captain-restart-a");
        Long team2CaptainId = createUser("captain-restart-b");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                null
        );

        entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(
                        playerId(snapshot, 1, "captain-restart-a"),
                        playerId(snapshot, 1, "a")
                )),
                actor(team1CaptainId, "captain-restart-a")
        );
        entrySubmissionCommandService.submitEntries(
                snapshot.getSession().getId(),
                submitRequest(List.of(
                        playerId(snapshot, 2, "captain-restart-b"),
                        playerId(snapshot, 2, "b")
                )),
                actor(team2CaptainId, "captain-restart-b")
        );

        EntrySubmissionSnapshotResponseDto restarted = entrySubmissionCommandService.restartSession(
                snapshot.getSession().getId(),
                actor(ownerId, "entry-owner-restart")
        );

        assertThat(restarted.getSession().getStatus()).isEqualTo(EntrySubmissionSessionEntity.STATUS_SUBMITTING);
        assertThat(restarted.getSession().getCompletedAt()).isNull();
        assertThat(restarted.getSession().getSetCount()).isEqualTo(2);
        assertThat(playerNames(restarted, 1)).containsExactly("captain-restart-a", "a");
        assertThat(playerNames(restarted, 2)).containsExactly("captain-restart-b", "b");
        assertThat(restarted.getTeams()).extracting("submitted").containsExactly(false, false);
        assertThat(restarted.getEntries()).isEmpty();
        assertThat(restarted.getMatches()).extracting("team1PlayerName").containsOnlyNulls();
        assertThat(restarted.getMatches()).extracting("team2PlayerName").containsOnlyNulls();
        assertThat(restarted.getPermissions().isCanRestart()).isTrue();
    }

    @Test
    void only_owner_or_admin_can_restart_session() {
        Long ownerId = createUser("entry-owner-restart-denied");
        Long team1CaptainId = createUser("captain-restart-denied-a");
        Long team2CaptainId = createUser("captain-restart-denied-b");
        EntrySubmissionSnapshotResponseDto snapshot = createSession(
                ownerId,
                team1CaptainId,
                team2CaptainId,
                List.of("a"),
                List.of("b"),
                null
        );

        assertThatThrownBy(() -> entrySubmissionCommandService.restartSession(
                snapshot.getSession().getId(),
                actor(team1CaptainId, "captain-restart-denied-a")
        )).hasMessageContaining("session owner or an admin");
    }

    private EntrySubmissionSnapshotResponseDto createSession(
            Long ownerId,
            Long team1CaptainId,
            Long team2CaptainId,
            List<String> team1PlayerNames,
            List<String> team2PlayerNames,
            Integer setCount
    ) {
        ResponseDto<EntrySubmissionSnapshotResponseDto> response = entrySubmissionService.createSession(
                createRequest("entry session", team1CaptainId, team2CaptainId, team1PlayerNames, team2PlayerNames, setCount),
                actor(ownerId, "owner")
        );
        assertThat(response.getData()).isNotNull();
        return response.getData();
    }

    private EntrySubmissionSessionCreateRequestDto createRequest(
            String title,
            Long team1CaptainId,
            Long team2CaptainId,
            List<String> team1PlayerNames,
            List<String> team2PlayerNames,
            Integer setCount
    ) {
        EntrySubmissionSessionCreateRequestDto requestDto = new EntrySubmissionSessionCreateRequestDto();
        requestDto.setTitle(title);
        requestDto.setTeam1CaptainUserId(team1CaptainId);
        requestDto.setTeam2CaptainUserId(team2CaptainId);
        requestDto.setTeam1PlayerNames(team1PlayerNames);
        requestDto.setTeam2PlayerNames(team2PlayerNames);
        requestDto.setSetCount(setCount);
        return requestDto;
    }

    private EntrySubmissionSubmitRequestDto submitRequest(List<Long> playerIds) {
        EntrySubmissionSubmitRequestDto requestDto = new EntrySubmissionSubmitRequestDto();
        List<EntrySubmissionEntryRequestDto> entries = new java.util.ArrayList<>();
        for (int index = 0; index < playerIds.size(); index++) {
            EntrySubmissionEntryRequestDto entry = new EntrySubmissionEntryRequestDto();
            entry.setSetNo(index + 1);
            entry.setPlayerId(playerIds.get(index));
            entries.add(entry);
        }
        requestDto.setEntries(entries);
        return requestDto;
    }

    private List<String> playerNames(EntrySubmissionSnapshotResponseDto snapshot, int teamDisplayOrder) {
        Long teamId = snapshot.getTeams().stream()
                .filter(team -> team.getDisplayOrder() == teamDisplayOrder)
                .findFirst()
                .orElseThrow()
                .getId();
        return snapshot.getPlayers().stream()
                .filter(player -> player.getEntrySubmissionTeamId().equals(teamId))
                .map(player -> player.getPlayerName())
                .toList();
    }

    private Long playerId(EntrySubmissionSnapshotResponseDto snapshot, int teamDisplayOrder, String playerName) {
        Long teamId = snapshot.getTeams().stream()
                .filter(team -> team.getDisplayOrder() == teamDisplayOrder)
                .findFirst()
                .orElseThrow()
                .getId();
        return snapshot.getPlayers().stream()
                .filter(player -> player.getEntrySubmissionTeamId().equals(teamId))
                .filter(player -> playerName.equals(player.getPlayerName()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private EntrySubmissionActor actor(Long userPk, String userId) {
        return new EntrySubmissionActor(userPk, userId, "ROLE_USER");
    }

    private Long createUser(String userId) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(userId)
                .status("ACTIVE")
                .userType("ROLE_USER")
                .build();
        return userRepository.save(user).getId();
    }
}
