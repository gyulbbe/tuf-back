package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderBulkReplaceRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickerResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionDetailResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionRequestDto;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
import io.github.gyulbbe.draft.dto.DraftTeamRequestDto;
import io.github.gyulbbe.draft.dto.DraftTeamResponseDto;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        DraftService.class,
        DraftLiveSessionTracker.class,
        DraftOrderPatternService.class,
        DraftQueryRepositoryImpl.class,
        QueryDslConfig.class,
        DraftPermissionService.class,
        DraftAdminService.class
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
        "spring.datasource.url=jdbc:h2:mem:draftdb;MODE=Oracle;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class DraftServiceTest {

    @Autowired
    private DraftService draftService;

    @Autowired
    private DraftAdminService draftAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DraftSessionRepository draftSessionRepository;

    @Autowired
    private DraftTeamRepository draftTeamRepository;

    @Autowired
    private DraftCandidateRepository draftCandidateRepository;

    @Autowired
    private DraftOrderRepository draftOrderRepository;

    @Autowired
    private DraftPickRepository draftPickRepository;

    @Test
    void authenticated_owner_can_create_session_and_owner_fields_are_exposed() {
        AuthActor owner = createActor("owner01", "Owner One", "ROLE_USER");

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.createSession(sessionRequest("Owner Session", 2, 60), owner);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getOwnerUserId()).isEqualTo(owner.userPk());
        assertThat(response.getData().getOwnerUserLoginId()).isEqualTo("owner01");
        assertThat(response.getData().getOwnerName()).isEqualTo("owner01");

        ResponseDto<DraftSessionDetailResponseDto> detail = draftService.getSession(response.getData().getId());
        assertThat(detail.getStatus()).isEqualTo(200);
        assertThat(detail.getData().getOwnerUserId()).isEqualTo(owner.userPk());
        assertThat(detail.getData().getOwnerUserLoginId()).isEqualTo("owner01");
        assertThat(detail.getData().getOwnerName()).isEqualTo("owner01");
        assertThat(detail.getData().getTeams()).extracting("teamName").containsExactly("1팀", "2팀");
    }

    @Test
    void anonymous_create_session_fails() {
        ResponseDto<DraftSessionDetailResponseDto> response = draftService.createSession(sessionRequest("Anonymous Session", 2, 60), null);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Authentication is required.");
    }

    @Test
    void owner_can_update_and_delete_own_session() {
        AuthActor owner = createActor("owner02", "Owner Two", "ROLE_USER");
        Long sessionId = createSession(owner, "Managed Session", 2, 60);

        DraftSessionRequestDto updateRequest = new DraftSessionRequestDto();
        updateRequest.setTitle("Managed Session Updated");
        updateRequest.setPickTimeSeconds(90);

        ResponseDto<DraftSessionDetailResponseDto> updateResponse = draftService.updateSession(sessionId, updateRequest, owner);
        ResponseDto<Void> deleteResponse = draftService.deleteSession(sessionId, owner);

        assertThat(updateResponse.getStatus()).isEqualTo(200);
        assertThat(updateResponse.getData().getTitle()).isEqualTo("Managed Session Updated");
        assertThat(updateResponse.getData().getPickTimeSeconds()).isEqualTo(90);
        assertThat(draftSessionRepository.findById(sessionId)).isEmpty();
        assertThat(deleteResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void owner_can_manage_team_candidate_and_order_mutations() {
        AuthActor owner = createActor("owner03", "Owner Three", "ROLE_USER");
        AuthActor picker = createActor("picker01", "Picker One", "ROLE_USER");
        Long candidateUserId = createUser("candidate01", "Candidate One", "ROLE_USER", "S", "ZERG");

        Long sessionId = createSession(owner, "Owner Managed Draft", 2, 60);
        Long teamAId = createTeam(owner, sessionId, "Team A", 1);
        Long teamBId = createTeam(owner, sessionId, "Team B", 2);

        ResponseDto<DraftSessionDetailResponseDto> teamUpdateResponse = draftService.updateTeam(teamAId, teamRequest(sessionId, "Team A+", 1), owner);
        ResponseDto<DraftSessionDetailResponseDto> assignPickerResponse = draftAdminService.assignPicker(teamAId, picker.userPk(), owner);

        DraftCandidateRequestDto createCandidateRequest = candidateRequest(sessionId, candidateUserId, "Candidate One", "ZERG");
        ResponseDto<DraftSessionDetailResponseDto> createCandidateResponse = draftService.createCandidate(createCandidateRequest, owner);

        DraftCandidateRequestDto updateCandidateRequest = new DraftCandidateRequestDto();
        updateCandidateRequest.setCandidateName("Candidate One Updated");
        updateCandidateRequest.setRace("PROTOSS");
        ResponseDto<DraftSessionDetailResponseDto> updateCandidateResponse =
                draftService.updateCandidate(sessionId, candidateUserId, updateCandidateRequest, owner);

        ResponseDto<DraftSessionDetailResponseDto> createOrder1 = draftService.createOrder(orderRequest(sessionId, 1L, teamAId), owner);
        ResponseDto<DraftSessionDetailResponseDto> createOrder2 = draftService.createOrder(orderRequest(sessionId, 2L, teamBId), owner);
        ResponseDto<DraftSessionDetailResponseDto> updateOrder2 =
                draftService.updateOrder(sessionId, 2L, orderRequest(sessionId, 2L, teamAId), owner);
        ResponseDto<DraftSessionDetailResponseDto> deleteOrder2 = draftService.deleteOrder(sessionId, 2L, owner);
        ResponseDto<DraftSessionDetailResponseDto> deleteCandidateResponse = draftService.deleteCandidate(sessionId, candidateUserId, owner);
        ResponseDto<Void> deleteTeamResponse = draftService.deleteTeam(teamBId, owner);

        assertThat(teamUpdateResponse.getStatus()).isEqualTo(200);
        assertThat(teamUpdateResponse.getData().getTeams()).filteredOn("id", teamAId)
                .extracting("teamName").containsExactly("Team A+");
        assertThat(assignPickerResponse.getStatus()).isEqualTo(200);
        assertThat(assignPickerResponse.getData().getTeams()).filteredOn("id", teamAId)
                .extracting("pickerUserId", "pickerUserLoginId")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(picker.userPk(), "picker01"));
        assertThat(createCandidateResponse.getStatus()).isEqualTo(200);
        assertThat(createCandidateResponse.getData().getCandidates()).filteredOn("candidateUserId", candidateUserId)
                .extracting("candidateUserLoginId").containsExactly("candidate01");
        assertThat(updateCandidateResponse.getData().getCandidates()).filteredOn("candidateUserId", candidateUserId)
                .extracting("candidateName", "candidateUserLoginId", "tier", "race")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("candidate01", "candidate01", "S", "PROTOSS"));
        assertThat(createOrder1.getStatus()).isEqualTo(200);
        assertThat(createOrder2.getStatus()).isEqualTo(200);
        assertThat(updateOrder2.getData().getOrders()).filteredOn("pickNo", 2L)
                .extracting("draftTeamId").containsExactly(teamAId);
        assertThat(deleteOrder2.getStatus()).isEqualTo(200);
        assertThat(deleteCandidateResponse.getStatus()).isEqualTo(200);
        assertThat(deleteTeamResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void owner_can_manage_legacy_pick_mutation() {
        AuthActor owner = createActor("owner04", "Owner Four", "ROLE_USER");
        Long pickerUserId = createUser("picker02", "Picker Two", "ROLE_USER");
        Long candidate1Id = createUser("candidate02", "Candidate Two", "ROLE_USER");
        Long candidate2Id = createUser("candidate03", "Candidate Three", "ROLE_USER");

        Long sessionId = createSession(owner, "Legacy Pick Session", 2, 45);
        Long teamAId = createTeam(owner, sessionId, "Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue", 2);
        draftAdminService.assignPicker(teamAId, pickerUserId, owner);
        draftAdminService.assignPicker(teamBId, pickerUserId, owner);
        draftService.createCandidate(candidateRequest(sessionId, candidate1Id, "Candidate Two", "ZERG"), owner);
        draftService.createCandidate(candidateRequest(sessionId, candidate2Id, "Candidate Three", "TERRAN"), owner);
        draftService.createOrder(orderRequest(sessionId, 1L, teamAId), owner);
        draftService.createOrder(orderRequest(sessionId, 2L, teamBId), owner);
        draftService.updateSession(sessionId, liveSessionRequest(teamAId), owner);

        DraftPickRequestDto createPickRequest = pickRequest(sessionId, 1L, teamAId, candidate1Id, pickerUserId);
        ResponseDto<DraftPickResponseDto> createPickResponse = draftService.createPick(createPickRequest, owner);

        ResponseDto<DraftCandidateResponseDto> candidateResponse = draftService.getCandidate(sessionId, candidate1Id);
        ResponseDto<DraftSessionDetailResponseDto> sessionResponse = draftService.getSession(sessionId);
        ResponseDto<Void> deletePickResponse = draftService.deletePick(sessionId, 1L, owner);

        assertThat(createPickResponse.getStatus()).isEqualTo(200);
        assertThat(candidateResponse.getData().getStatus()).isEqualTo("PICKED");
        assertThat(candidateResponse.getData().getPickedDraftTeamId()).isEqualTo(teamAId);
        assertThat(sessionResponse.getData().getCurrentPickNo()).isEqualTo(2);
        assertThat(sessionResponse.getData().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(deletePickResponse.getStatus()).isEqualTo(200);
        assertThat(draftPickRepository.findAllByDraftSessionIdOrderByPickNoAsc(sessionId)).isEmpty();
    }

    @Test
    void legacy_create_pick_repeats_order_pattern_until_waiting_candidates_are_exhausted() {
        AuthActor owner = createActor("owner-repeat-pick", "Owner Repeat Pick", "ROLE_USER");
        Long pickerUserId = createUser("picker-repeat-pick", "Picker Repeat Pick", "ROLE_USER");
        Long candidate1Id = createUser("candidate-repeat-pick-1", "Candidate Repeat Pick One", "ROLE_USER");
        Long candidate2Id = createUser("candidate-repeat-pick-2", "Candidate Repeat Pick Two", "ROLE_USER");
        Long candidate3Id = createUser("candidate-repeat-pick-3", "Candidate Repeat Pick Three", "ROLE_USER");

        Long sessionId = createSession(owner, "Legacy Repeating Pick Session", 2, 45);
        Long teamAId = createTeam(owner, sessionId, "Repeat Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Repeat Blue", 2);
        draftAdminService.assignPicker(teamAId, pickerUserId, owner);
        draftAdminService.assignPicker(teamBId, pickerUserId, owner);
        draftService.createCandidate(candidateRequest(sessionId, candidate1Id, "Candidate Repeat Pick One", "ZERG"), owner);
        draftService.createCandidate(candidateRequest(sessionId, candidate2Id, "Candidate Repeat Pick Two", "TERRAN"), owner);
        draftService.createCandidate(candidateRequest(sessionId, candidate3Id, "Candidate Repeat Pick Three", "PROTOSS"), owner);
        draftService.createOrder(orderRequest(sessionId, 1L, teamAId), owner);
        draftService.createOrder(orderRequest(sessionId, 2L, teamBId), owner);
        draftService.updateSession(sessionId, liveSessionRequest(teamAId), owner);

        ResponseDto<DraftPickResponseDto> pick1 =
                draftService.createPick(pickRequest(sessionId, 1L, teamAId, candidate1Id, pickerUserId), owner);
        ResponseDto<DraftPickResponseDto> pick2 =
                draftService.createPick(pickRequest(sessionId, 2L, teamBId, candidate2Id, pickerUserId), owner);
        ResponseDto<DraftSessionDetailResponseDto> afterSecondPick = draftService.getSession(sessionId);
        ResponseDto<DraftPickResponseDto> pick3 =
                draftService.createPick(pickRequest(sessionId, 3L, teamAId, candidate3Id, pickerUserId), owner);
        ResponseDto<DraftSessionDetailResponseDto> afterFinalPick = draftService.getSession(sessionId);

        assertThat(pick1.getStatus()).isEqualTo(200);
        assertThat(pick2.getStatus()).isEqualTo(200);
        assertThat(afterSecondPick.getData().getStatus()).isEqualTo("LIVE");
        assertThat(afterSecondPick.getData().getCurrentPickNo()).isEqualTo(3);
        assertThat(afterSecondPick.getData().getCurrentDraftTeamId()).isEqualTo(teamAId);
        assertThat(afterSecondPick.getData().getOrders()).filteredOn("pickNo", 3L)
                .extracting("draftTeamId")
                .containsExactly(teamAId);
        assertThat(pick3.getStatus()).isEqualTo(200);
        assertThat(afterFinalPick.getData().getStatus()).isEqualTo("FINISHED");
        assertThat(afterFinalPick.getData().getCurrentDraftTeamId()).isNull();
    }

    @Test
    void owner_can_replace_orders_in_one_request() {
        AuthActor owner = createActor("owner-orders", "Owner Orders", "ROLE_USER");
        Long sessionId = createSession(owner, "Bulk Orders", 2, 60);
        Long teamAId = createTeam(owner, sessionId, "Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue", 2);
        draftService.createOrder(orderRequest(sessionId, 1L, teamAId), owner);

        DraftOrderBulkReplaceRequestDto requestDto = new DraftOrderBulkReplaceRequestDto();
        requestDto.setOrders(List.of(
                bulkOrderRequest(1L, 1L, teamBId),
                bulkOrderRequest(1L, 2L, teamAId)
        ));

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.replaceOrders(sessionId, requestDto, owner);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getOrders()).extracting("pickNo", "draftTeamId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, teamBId),
                        org.assertj.core.groups.Tuple.tuple(2L, teamAId)
                );
        assertThat(draftOrderRepository.countByDraftSessionId(sessionId)).isEqualTo(2);
    }

    @Test
    void replace_orders_rejects_duplicate_pick_no() {
        AuthActor owner = createActor("owner-dup-orders", "Owner Duplicate Orders", "ROLE_USER");
        Long sessionId = createSession(owner, "Duplicate Orders", 2, 60);
        Long teamAId = createTeam(owner, sessionId, "Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Blue", 2);

        DraftOrderBulkReplaceRequestDto requestDto = new DraftOrderBulkReplaceRequestDto();
        requestDto.setOrders(List.of(
                bulkOrderRequest(1L, 1L, teamAId),
                bulkOrderRequest(1L, 1L, teamBId)
        ));

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.replaceOrders(sessionId, requestDto, owner);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getMessage()).contains("Duplicate pick number");
        assertThat(draftOrderRepository.countByDraftSessionId(sessionId)).isZero();
    }

    @Test
    void owner_admin_not_user_cannot_mutate_foreign_session() {
        AuthActor owner = createActor("owner05", "Owner Five", "ROLE_USER");
        AuthActor otherUser = createActor("other01", "Other One", "ROLE_USER");
        Long sessionId = createSession(owner, "Protected Session", 2, 60);

        DraftSessionRequestDto updateRequest = new DraftSessionRequestDto();
        updateRequest.setTitle("Illegal Update");

        ResponseDto<DraftSessionDetailResponseDto> updateResponse = draftService.updateSession(sessionId, updateRequest, otherUser);
        ResponseDto<DraftTeamResponseDto> teamCreateResponse = draftService.createTeam(teamRequest(sessionId, "Illegal Team", 1), otherUser);

        assertThat(updateResponse.getStatus()).isEqualTo(500);
        assertThat(updateResponse.getMessage()).contains("session owner or an administrator");
        assertThat(teamCreateResponse.getStatus()).isEqualTo(500);
        assertThat(teamCreateResponse.getMessage()).contains("session owner or an administrator");
    }

    @Test
    void admin_can_manage_foreign_session() {
        AuthActor owner = createActor("owner06", "Owner Six", "ROLE_USER");
        AuthActor admin = createActor("admin01", "Admin One", "ROLE_ADMIN");
        Long sessionId = createSession(owner, "Admin Managed Session", 2, 60);

        DraftSessionRequestDto updateRequest = new DraftSessionRequestDto();
        updateRequest.setTitle("Admin Updated Session");

        ResponseDto<DraftSessionDetailResponseDto> updateResponse = draftService.updateSession(sessionId, updateRequest, admin);
        ResponseDto<DraftTeamResponseDto> createTeamResponse = draftService.createTeam(teamRequest(sessionId, "Admin Team", 1), admin);

        assertThat(updateResponse.getStatus()).isEqualTo(200);
        assertThat(updateResponse.getData().getTitle()).isEqualTo("Admin Updated Session");
        assertThat(createTeamResponse.getStatus()).isEqualTo(200);
        assertThat(createTeamResponse.getData().getTeamName()).isEqualTo("Admin Team");
    }

    private Long createSession(AuthActor actor, String title, int teamCount, int pickTimeSeconds) {
        return draftService.createSession(sessionRequest(title, teamCount, pickTimeSeconds), actor).getData().getId();
    }

    private Long createTeam(AuthActor actor, Long sessionId, String teamName, int displayOrder) {
        return draftService.createTeam(teamRequest(sessionId, teamName, displayOrder), actor).getData().getId();
    }

    private DraftSessionRequestDto sessionRequest(String title, int teamCount, int pickTimeSeconds) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle(title);
        requestDto.setStatus("READY");
        requestDto.setTeamCount(teamCount);
        requestDto.setPickTimeSeconds(pickTimeSeconds);
        requestDto.setCurrentPickNo(1);
        return requestDto;
    }

    private DraftSessionRequestDto liveSessionRequest(Long currentDraftTeamId) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setStatus("LIVE");
        requestDto.setCurrentPickNo(1);
        requestDto.setCurrentDraftTeamId(currentDraftTeamId);
        return requestDto;
    }

    private DraftTeamRequestDto teamRequest(Long sessionId, String teamName, int displayOrder) {
        DraftTeamRequestDto requestDto = new DraftTeamRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setTeamName(teamName);
        requestDto.setDisplayOrder(displayOrder);
        return requestDto;
    }

    private DraftCandidateRequestDto candidateRequest(Long sessionId, Long candidateUserId, String candidateName, String race) {
        DraftCandidateRequestDto requestDto = new DraftCandidateRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setCandidateUserId(candidateUserId);
        requestDto.setCandidateName(candidateName);
        requestDto.setRace(race);
        requestDto.setStatus("WAITING");
        return requestDto;
    }

    private DraftOrderRequestDto orderRequest(Long sessionId, Long pickNo, Long draftTeamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(draftTeamId);
        return requestDto;
    }

    private DraftOrderRequestDto bulkOrderRequest(Long roundNo, Long pickNo, Long draftTeamId) {
        DraftOrderRequestDto requestDto = new DraftOrderRequestDto();
        requestDto.setRoundNo(roundNo);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(draftTeamId);
        return requestDto;
    }

    private DraftPickRequestDto pickRequest(Long sessionId, Long pickNo, Long teamId, Long candidateUserId, Long pickedByUserId) {
        DraftPickRequestDto requestDto = new DraftPickRequestDto();
        requestDto.setDraftSessionId(sessionId);
        requestDto.setPickNo(pickNo);
        requestDto.setDraftTeamId(teamId);
        requestDto.setCandidateUserId(candidateUserId);
        requestDto.setPickedByUserId(pickedByUserId);
        return requestDto;
    }

    private AuthActor createActor(String userId, String name, String role) {
        Long userPk = createUser(userId, name, role);
        return new AuthActor(userPk, userId, role);
    }

    private Long createUser(String userId, String name, String role) {
        return createUser(userId, name, role, null, null);
    }

    private Long createUser(String userId, String name, String role, String tier, String race) {
        UserEntity user = UserEntity.builder()
                .userId(userId)
                .password("password")
                .name(name)
                .tier(tier)
                .race(race)
                .status("ACTIVE")
                .userType(role)
                .build();
        return userRepository.save(user).getId();
    }
}
