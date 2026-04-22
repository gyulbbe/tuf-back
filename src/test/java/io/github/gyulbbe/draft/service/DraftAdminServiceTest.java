package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftPickerResponseDto;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
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
@Import({DraftAdminService.class, DraftPermissionService.class})
@EntityScan(basePackageClasses = {
        DraftSessionEntity.class,
        DraftTeamEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        UserRepository.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:draftadmindb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftAdminServiceTest {

    @Autowired
    private DraftAdminService draftAdminService;

    @Autowired
    private DraftPermissionService draftPermissionService;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftTeamRepository draftTeamRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void owner_can_assign_and_reassign_picker_on_own_session() {
        AuthActor owner = createActor("owner01", "Owner One", "ROLE_USER");
        Long firstPickerId = createUser("picker01", "Picker One", "ROLE_USER", "ACTIVE");
        Long secondPickerId = createUser("picker02", "Picker Two", "ROLE_USER", "ACTIVE");
        Long sessionId = createSession(owner.userPk());
        Long teamId = createTeam(sessionId, "Team A", 1);

        ResponseDto<DraftPickerResponseDto> firstAssign = draftAdminService.assignPicker(teamId, firstPickerId, owner);
        ResponseDto<DraftPickerResponseDto> secondAssign = draftAdminService.assignPicker(teamId, secondPickerId, owner);

        assertThat(firstAssign.getStatus()).isEqualTo(200);
        assertThat(secondAssign.getStatus()).isEqualTo(200);
        assertThat(secondAssign.getData().getPickerUserId()).isEqualTo(secondPickerId);
        assertThat(draftPermissionService.canPickForTeam(teamId, firstPickerId)).isFalse();
        assertThat(draftPermissionService.canPickForTeam(teamId, secondPickerId)).isTrue();
    }

    @Test
    void admin_can_assign_picker_on_foreign_session() {
        AuthActor owner = createActor("owner02", "Owner Two", "ROLE_USER");
        AuthActor admin = createActor("admin01", "Admin One", "ROLE_ADMIN");
        Long pickerId = createUser("picker03", "Picker Three", "ROLE_USER", "ACTIVE");
        Long sessionId = createSession(owner.userPk());
        Long teamId = createTeam(sessionId, "Team B", 1);

        ResponseDto<DraftPickerResponseDto> response = draftAdminService.assignPicker(teamId, pickerId, admin);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getPickerUserId()).isEqualTo(pickerId);
        assertThat(draftPermissionService.canPickForTeam(teamId, pickerId)).isTrue();
    }

    @Test
    void owner_admin_not_user_cannot_assign_picker_on_foreign_session() {
        AuthActor owner = createActor("owner03", "Owner Three", "ROLE_USER");
        AuthActor otherUser = createActor("other01", "Other One", "ROLE_USER");
        Long pickerId = createUser("picker04", "Picker Four", "ROLE_USER", "ACTIVE");
        Long sessionId = createSession(owner.userPk());
        Long teamId = createTeam(sessionId, "Team C", 1);

        ResponseDto<DraftPickerResponseDto> response = draftAdminService.assignPicker(teamId, pickerId, otherUser);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("session owner or an administrator");
        assertThat(draftPermissionService.canPickForTeam(teamId, pickerId)).isFalse();
    }

    @Test
    void inactive_user_cannot_be_assigned_as_picker() {
        AuthActor owner = createActor("owner04", "Owner Four", "ROLE_USER");
        Long inactiveUserId = createUser("picker05", "Inactive Picker", "ROLE_USER", "INACTIVE");
        Long sessionId = createSession(owner.userPk());
        Long teamId = createTeam(sessionId, "Team D", 1);

        ResponseDto<DraftPickerResponseDto> response = draftAdminService.assignPicker(teamId, inactiveUserId, owner);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Only ACTIVE users");
        assertThat(draftPermissionService.canPickForTeam(teamId, inactiveUserId)).isFalse();
    }

    private Long createSession(Long ownerUserId) {
        DraftSessionEntity entity = DraftSessionEntity.builder()
                .title("Draft Session")
                .ownerUserId(ownerUserId)
                .status("READY")
                .teamCount(2)
                .pickTimeSeconds(30)
                .currentPickNo(1)
                .build();
        return draftSessionRepository.save(entity).getId();
    }

    private Long createTeam(Long sessionId, String teamName, int displayOrder) {
        DraftTeamEntity entity = DraftTeamEntity.builder()
                .draftSessionId(sessionId)
                .teamName(teamName)
                .displayOrder(displayOrder)
                .pickerUserId(null)
                .build();
        return draftTeamRepository.save(entity).getId();
    }

    private AuthActor createActor(String userId, String name, String role) {
        Long userPk = createUser(userId, name, role, "ACTIVE");
        return new AuthActor(userPk, userId, role);
    }

    private Long createUser(String userId, String name, String role, String status) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status(status)
                .userType(role)
                .build();
        return userRepository.save(user).getId();
    }
}
