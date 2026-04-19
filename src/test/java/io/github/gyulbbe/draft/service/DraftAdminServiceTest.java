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
    void 관리자는_팀_픽커를_지정하고_다시_변경할_수_있다() {
        Long firstPickerId = createUser("picker01", "첫번째", "ACTIVE");
        Long secondPickerId = createUser("picker02", "두번째", "ACTIVE");
        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "A팀", 1);

        AuthActor admin = new AuthActor(999L, "admin", "ROLE_ADMIN");

        ResponseDto<DraftPickerResponseDto> firstAssign = draftAdminService.assignPicker(teamId, firstPickerId, admin);
        ResponseDto<DraftPickerResponseDto> secondAssign = draftAdminService.assignPicker(teamId, secondPickerId, admin);

        assertThat(firstAssign.getStatus()).isEqualTo(200);
        assertThat(secondAssign.getStatus()).isEqualTo(200);
        assertThat(secondAssign.getData().getPickerUserId()).isEqualTo(secondPickerId);
        assertThat(draftPermissionService.canPickForTeam(teamId, firstPickerId)).isFalse();
        assertThat(draftPermissionService.canPickForTeam(teamId, secondPickerId)).isTrue();
    }

    @Test
    void 관리자가_아니면_픽커를_지정할_수_없다() {
        Long pickerId = createUser("picker03", "일반유저", "ACTIVE");
        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "B팀", 1);

        AuthActor normalUser = new AuthActor(pickerId, "picker03", "ROLE_USER");

        ResponseDto<DraftPickerResponseDto> response = draftAdminService.assignPicker(teamId, pickerId, normalUser);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("관리자");
        assertThat(draftPermissionService.canPickForTeam(teamId, pickerId)).isFalse();
    }

    @Test
    void 비활성_유저는_픽커로_지정할_수_없다() {
        Long inactiveUserId = createUser("picker04", "비활성", "INACTIVE");
        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "C팀", 1);

        AuthActor admin = new AuthActor(1000L, "admin", "ROLE_MANAGER");

        ResponseDto<DraftPickerResponseDto> response = draftAdminService.assignPicker(teamId, inactiveUserId, admin);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("ACTIVE");
        assertThat(draftPermissionService.canPickForTeam(teamId, inactiveUserId)).isFalse();
    }

    private Long createSession() {
        DraftSessionEntity entity = DraftSessionEntity.builder()
                .title("권한 테스트 세션")
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
