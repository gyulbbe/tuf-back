package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftTeamOperatorResponseDto;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.entity.DraftTeamOperatorEntity;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamOperatorRepository;
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
        DraftTeamOperatorEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftTeamOperatorRepository.class,
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
    private DraftTeamOperatorRepository draftTeamOperatorRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 관리자는_팀별_단일_픽권한자를_지정할수있다() {
        Long captainId = createUser("captain01", "팀장");
        Long viceCaptainId = createUser("vice01", "부팀장");
        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "A팀", 1);

        createOperator(teamId, captainId, "CAPTAIN");
        createOperator(teamId, viceCaptainId, "VICE_CAPTAIN");

        AuthActor admin = new AuthActor(999L, "admin", "ROLE_ADMIN");

        ResponseDto<DraftTeamOperatorResponseDto> firstAssign = draftAdminService.assignPicker(teamId, captainId, admin);
        ResponseDto<DraftTeamOperatorResponseDto> secondAssign = draftAdminService.assignPicker(teamId, viceCaptainId, admin);

        assertThat(firstAssign.getStatus()).isEqualTo(200);
        assertThat(secondAssign.getStatus()).isEqualTo(200);
        assertThat(secondAssign.getData().getOperatorUserId()).isEqualTo(viceCaptainId);
        assertThat(secondAssign.getData().getCanPick()).isEqualTo("Y");
        assertThat(draftPermissionService.canPickForTeam(teamId, captainId)).isFalse();
        assertThat(draftPermissionService.canPickForTeam(teamId, viceCaptainId)).isTrue();
    }

    @Test
    void 관리자가_아니면_픽권한자를_지정할수없다() {
        Long captainId = createUser("captain02", "팀장2");
        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "B팀", 1);
        createOperator(teamId, captainId, "CAPTAIN");

        AuthActor normalUser = new AuthActor(captainId, "captain02", "ROLE_USER");

        ResponseDto<DraftTeamOperatorResponseDto> response = draftAdminService.assignPicker(teamId, captainId, normalUser);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("관리자");
        assertThat(draftPermissionService.canPickForTeam(teamId, captainId)).isFalse();
    }

    @Test
    void 활성_팀장_부팀장만_픽권한자로_지정할수있다() {
        Long operatorId = createUser("operator01", "운영자");
        Long sessionId = createSession();
        Long teamId = createTeam(sessionId, "C팀", 1);
        createOperator(teamId, operatorId, "OPERATOR");

        AuthActor admin = new AuthActor(1000L, "admin", "ROLE_MANAGER");

        ResponseDto<DraftTeamOperatorResponseDto> response = draftAdminService.assignPicker(teamId, operatorId, admin);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("팀장 또는 부팀장");
        assertThat(draftPermissionService.canPickForTeam(teamId, operatorId)).isFalse();
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
                .build();
        return draftTeamRepository.save(entity).getId();
    }

    private void createOperator(Long teamId, Long operatorUserId, String role) {
        DraftTeamOperatorEntity entity = DraftTeamOperatorEntity.builder()
                .draftTeamId(teamId)
                .operatorUserId(operatorUserId)
                .role(role)
                .isActive("Y")
                .canPick("N")
                .build();
        draftTeamOperatorRepository.save(entity);
    }

    private Long createUser(String userId, String name) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .status("ACTIVE")
                .userType("ROLE_USER")
                .build();
        return userRepository.save(user).getId();
    }
}
