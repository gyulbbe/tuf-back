package io.github.gyulbbe.rpsdraft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionDetailResponseDto;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.RpsDraftTeamEntity;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftCandidateRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftPickRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftQueryRepositoryImpl;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftSessionRepository;
import io.github.gyulbbe.rpsdraft.repository.RpsDraftTeamRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        RpsDraftQueryRepositoryImpl.class,
        RpsDraftPermissionService.class,
        RpsDraftService.class,
        RpsDraftAdminService.class
})
@EntityScan(basePackageClasses = {
        RpsDraftSessionEntity.class,
        RpsDraftTeamEntity.class,
        RpsDraftCandidateEntity.class,
        RpsDraftPickEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        RpsDraftSessionRepository.class,
        RpsDraftTeamRepository.class,
        RpsDraftCandidateRepository.class,
        RpsDraftPickRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rpsdraftadmindb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class RpsDraftAdminServiceTest {

    @Autowired
    private RpsDraftAdminService rpsDraftAdminService;

    @Autowired
    private RpsDraftSessionRepository rpsDraftSessionRepository;

    @Autowired
    private RpsDraftTeamRepository rpsDraftTeamRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void owner_can_assign_picker() {
        Long ownerId = createUser("owner10", "owner10", "ACTIVE");
        Long initialPickerId = createUser("initial10", "initial10", "ACTIVE");
        Long pickerId = createUser("picker10", "Picker Real Name", "ACTIVE");
        Long sessionId = createSession(ownerId);
        Long teamId = createTeam(sessionId, "team-1", 1, initialPickerId);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftAdminService.assignPicker(
                sessionId,
                teamId,
                pickerId,
                new RpsDraftActor(ownerId, "owner10", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getTeams()).filteredOn("id", teamId)
                .extracting("pickerUserId", "pickerUserLoginId", "pickerName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(pickerId, "picker10", "picker10"));
        assertThat(response.getData().getTeams()).filteredOn("id", teamId)
                .allSatisfy(team -> {
                    assertThat(team.getPickerUserLoginId()).isNotEqualTo("Picker Real Name");
                    assertThat(team.getPickerUserLoginId()).isNotEqualTo(String.valueOf(pickerId));
                });
        assertThat(rpsDraftTeamRepository.findById(teamId)).get()
                .extracting(RpsDraftTeamEntity::getPickerUserId)
                .isEqualTo(pickerId);
    }

    @Test
    void assignPicker_rejects_duplicate_picker_in_same_session() {
        Long ownerId = createUser("owner11", "owner11", "ACTIVE");
        Long pickerId = createUser("picker11", "picker11", "ACTIVE");
        Long team2InitialPickerId = createUser("picker11b", "picker11b", "ACTIVE");
        Long sessionId = createSession(ownerId);
        Long team1Id = createTeam(sessionId, "team-1", 1, pickerId);
        Long team2Id = createTeam(sessionId, "team-2", 2, team2InitialPickerId);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftAdminService.assignPicker(
                sessionId,
                team2Id,
                pickerId,
                new RpsDraftActor(ownerId, "owner11", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("already assigned");
        assertThat(rpsDraftTeamRepository.findById(team1Id)).get()
                .extracting(RpsDraftTeamEntity::getPickerUserId)
                .isEqualTo(pickerId);
    }

    @Test
    void assignPicker_rejects_non_owner() {
        Long ownerId = createUser("owner12", "owner12", "ACTIVE");
        Long otherId = createUser("other12", "other12", "ACTIVE");
        Long initialPickerId = createUser("initial12", "initial12", "ACTIVE");
        Long pickerId = createUser("picker12", "picker12", "ACTIVE");
        Long sessionId = createSession(ownerId);
        Long teamId = createTeam(sessionId, "team-1", 1, initialPickerId);

        ResponseDto<RpsDraftSessionDetailResponseDto> response = rpsDraftAdminService.assignPicker(
                sessionId,
                teamId,
                pickerId,
                new RpsDraftActor(otherId, "other12", "ROLE_USER")
        );

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("session owner");
    }

    private Long createSession(Long ownerId) {
        return rpsDraftSessionRepository.save(
                RpsDraftSessionEntity.builder()
                        .title("picker session")
                        .ownerUserId(ownerId)
                        .build()
        ).getId();
    }

    private Long createTeam(Long sessionId, String teamName, int displayOrder, Long pickerUserId) {
        return rpsDraftTeamRepository.save(
                RpsDraftTeamEntity.builder()
                        .rpsDraftSessionId(sessionId)
                        .teamName(teamName)
                        .displayOrder(displayOrder)
                        .pickerUserId(pickerUserId)
                        .build()
        ).getId();
    }

    private Long createUser(String userId, String name, String status) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status(status)
                .userType("ROLE_USER")
                .build();
        return userRepository.save(user).getId();
    }
}
