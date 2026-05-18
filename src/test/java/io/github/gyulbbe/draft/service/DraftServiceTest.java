package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.config.QueryDslConfig;
import io.github.gyulbbe.draft.auth.AuthActor;
import io.github.gyulbbe.draft.dto.DraftCandidateRequestDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftHistoryPageResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderBulkReplaceRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderRequestDto;
import io.github.gyulbbe.draft.dto.DraftOrderResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickerResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickRequestDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftProleagueTeamPickerRequestDto;
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
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.LeagueParticipationEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamEntity;
import io.github.gyulbbe.league.entity.ProleagueTeamMemberEntity;
import io.github.gyulbbe.league.repository.LeagueRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamMemberRepository;
import io.github.gyulbbe.league.repository.ProleagueTeamRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        DraftService.class,
        DraftLiveSessionTracker.class,
        DraftOrderPatternService.class,
        ProleagueDraftRosterSyncService.class,
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
        LeagueEntity.class,
        LeagueParticipationEntity.class,
        ProleagueTeamEntity.class,
        ProleagueTeamMemberEntity.class,
        UserEntity.class
})
@EnableJpaRepositories(basePackageClasses = {
        DraftSessionRepository.class,
        DraftTeamRepository.class,
        DraftCandidateRepository.class,
        DraftOrderRepository.class,
        DraftPickRepository.class,
        LeagueRepository.class,
        ProleagueTeamRepository.class,
        ProleagueTeamMemberRepository.class,
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

    @Autowired
    private LeagueRepository leagueRepository;

    @Autowired
    private ProleagueTeamRepository proleagueTeamRepository;

    @Autowired
    private ProleagueTeamMemberRepository proleagueTeamMemberRepository;

    @Test
    void authenticated_owner_can_create_session_and_owner_fields_are_exposed() {
        AuthActor owner = createActor("owner01", "Owner One", "ROLE_USER");

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.createSession(sessionRequest("Owner Session", 2, 60), owner);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getOwnerUserId()).isEqualTo(owner.userPk());
        assertThat(response.getData().getOwnerUserLoginId()).isEqualTo("owner01");
        assertThat(response.getData().getOwnerName()).isEqualTo("owner01");
        assertThat(response.getData().getOrderMode()).isEqualTo("BASIC");

        ResponseDto<DraftSessionDetailResponseDto> detail = draftService.getSession(response.getData().getId());
        assertThat(detail.getStatus()).isEqualTo(200);
        assertThat(detail.getData().getOwnerUserId()).isEqualTo(owner.userPk());
        assertThat(detail.getData().getOwnerUserLoginId()).isEqualTo("owner01");
        assertThat(detail.getData().getOwnerName()).isEqualTo("owner01");
        assertThat(detail.getData().getOrderMode()).isEqualTo("BASIC");
        assertThat(detail.getData().getTeams()).extracting("teamName").containsExactly("1팀", "2팀");
    }

    @Test
    void anonymous_create_session_fails() {
        ResponseDto<DraftSessionDetailResponseDto> response = draftService.createSession(sessionRequest("Anonymous Session", 2, 60), null);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Authentication is required.");
    }

    @Test
    void create_session_with_proleague_link_loads_proleague_teams() {
        AuthActor owner = createActor("owner-proleague-link", "Owner Proleague Link", "ROLE_USER");
        Long leader1Id = createUser("pl-leader-1", "PL Leader One", "ROLE_USER");
        Long vice1Id = createUser("pl-vice-1", "PL Vice One", "ROLE_USER");
        Long leader2Id = createUser("pl-leader-2", "PL Leader Two", "ROLE_USER");
        Long vice2Id = createUser("pl-vice-2", "PL Vice Two", "ROLE_USER");
        Long leagueId = createProleague("Linked Proleague");
        Long team1Id = createProleagueTeam(leagueId, "Alpha", leader1Id, vice1Id, 1);
        Long team2Id = createProleagueTeam(leagueId, "Bravo", leader2Id, vice2Id, 2);

        DraftSessionRequestDto request = sessionRequest("Linked Draft", 2, 60);
        request.setProleagueId(leagueId);
        request.setProleagueTeamPickers(List.of(
                proleagueTeamPicker(team1Id, vice1Id),
                proleagueTeamPicker(team2Id, leader2Id)
        ));

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.createSession(request, owner);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getProleagueId()).isEqualTo(leagueId);
        assertThat(response.getData().getProleagueName()).isEqualTo("Linked Proleague");
        assertThat(response.getData().getTeams())
                .extracting("teamName", "proleagueTeamId", "pickerUserId")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Alpha", team1Id, vice1Id),
                        org.assertj.core.groups.Tuple.tuple("Bravo", team2Id, leader2Id)
                );
    }

    @Test
    void create_session_with_proleague_link_requires_picker_for_every_team() {
        AuthActor owner = createActor("owner-proleague-picker", "Owner Proleague Picker", "ROLE_USER");
        Long leader1Id = createUser("picker-leader-1", "Picker Leader One", "ROLE_USER");
        Long vice1Id = createUser("picker-vice-1", "Picker Vice One", "ROLE_USER");
        Long leader2Id = createUser("picker-leader-2", "Picker Leader Two", "ROLE_USER");
        Long vice2Id = createUser("picker-vice-2", "Picker Vice Two", "ROLE_USER");
        Long leagueId = createProleague("Picker Required Proleague");
        Long team1Id = createProleagueTeam(leagueId, "Alpha", leader1Id, vice1Id, 1);
        createProleagueTeam(leagueId, "Bravo", leader2Id, vice2Id, 2);

        DraftSessionRequestDto request = sessionRequest("Picker Required Draft", 2, 60);
        request.setProleagueId(leagueId);
        request.setProleagueTeamPickers(List.of(proleagueTeamPicker(team1Id, leader1Id)));

        ResponseDto<DraftSessionDetailResponseDto> response = draftService.createSession(request, owner);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getMessage()).contains("Picker is required");
    }

    @Test
    void linked_proleague_candidate_pool_rejects_leader_and_existing_member() {
        AuthActor owner = createActor("owner-proleague-candidate", "Owner Proleague Candidate", "ROLE_USER");
        Long leaderId = createUser("candidate-leader", "Candidate Leader", "ROLE_USER");
        Long viceId = createUser("candidate-vice", "Candidate Vice", "ROLE_USER");
        Long otherLeaderId = createUser("candidate-other-leader", "Candidate Other Leader", "ROLE_USER");
        Long otherViceId = createUser("candidate-other-vice", "Candidate Other Vice", "ROLE_USER");
        Long memberId = createUser("candidate-member", "Candidate Member", "ROLE_USER");
        Long leagueId = createProleague("Candidate Block Proleague");
        Long teamId = createProleagueTeam(leagueId, "Alpha", leaderId, viceId, 1);
        createProleagueTeam(leagueId, "Bravo", otherLeaderId, otherViceId, 2);
        proleagueTeamMemberRepository.save(ProleagueTeamMemberEntity.builder()
                .leagueId(leagueId)
                .proleagueTeamId(teamId)
                .userId(memberId)
                .source(ProleagueTeamMemberEntity.SOURCE_MANUAL)
                .status(ProleagueTeamMemberEntity.STATUS_ACTIVE)
                .build());
        Long sessionId = createLinkedSession(owner, leagueId, teamId, leaderId);

        ResponseDto<DraftSessionDetailResponseDto> leaderResponse =
                draftService.createCandidate(candidateRequest(sessionId, leaderId, "Candidate Leader", "ZERG"), owner);
        ResponseDto<DraftSessionDetailResponseDto> memberResponse =
                draftService.createCandidate(candidateRequest(sessionId, memberId, "Candidate Member", "TERRAN"), owner);

        assertThat(leaderResponse.getStatus()).isEqualTo(500);
        assertThat(leaderResponse.getMessage()).contains("leaders and vice leaders");
        assertThat(memberResponse.getStatus()).isEqualTo(500);
        assertThat(memberResponse.getMessage()).contains("Existing proleague team members");
    }

    @Test
    void finishing_linked_proleague_draft_syncs_picked_members() {
        AuthActor owner = createActor("owner-proleague-sync", "Owner Proleague Sync", "ROLE_USER");
        Long leaderId = createUser("sync-leader", "Sync Leader", "ROLE_USER");
        Long viceId = createUser("sync-vice", "Sync Vice", "ROLE_USER");
        Long otherLeaderId = createUser("sync-other-leader", "Sync Other Leader", "ROLE_USER");
        Long otherViceId = createUser("sync-other-vice", "Sync Other Vice", "ROLE_USER");
        Long candidateId = createUser("sync-candidate", "Sync Candidate", "ROLE_USER");
        Long leagueId = createProleague("Sync Proleague");
        Long teamId = createProleagueTeam(leagueId, "Alpha", leaderId, viceId, 1);
        createProleagueTeam(leagueId, "Bravo", otherLeaderId, otherViceId, 2);
        Long sessionId = createLinkedSession(owner, leagueId, teamId, leaderId);
        Long draftTeamId = draftTeamRepository.findAllByDraftSessionIdOrderByDisplayOrderAsc(sessionId).get(0).getId();
        draftService.createCandidate(candidateRequest(sessionId, candidateId, "Sync Candidate", "ZERG"), owner);

        ResponseDto<DraftPickResponseDto> response = draftService.createPick(
                pickRequest(sessionId, 1L, draftTeamId, candidateId, leaderId),
                owner
        );

        DraftSessionEntity session = draftSessionRepository.findById(sessionId).orElseThrow();
        List<ProleagueTeamMemberEntity> members =
                proleagueTeamMemberRepository.findAllByLeagueIdAndStatusOrderByDisplayOrderAscIdAsc(
                        leagueId,
                        ProleagueTeamMemberEntity.STATUS_ACTIVE
                );
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(session.getStatus()).isEqualTo("FINISHED");
        assertThat(members)
                .extracting(ProleagueTeamMemberEntity::getProleagueTeamId,
                        ProleagueTeamMemberEntity::getUserId,
                        ProleagueTeamMemberEntity::getSourceDraftSessionId,
                        ProleagueTeamMemberEntity::getDraftPickNo)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(teamId, candidateId, sessionId, 1L));
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
    void legacy_create_pick_extends_snake_order_after_mid_cycle_prefix() {
        AuthActor owner = createActor("owner-snake-pick", "Owner Snake Pick", "ROLE_USER");
        Long pickerUserId = createUser("picker-snake-pick", "Picker Snake Pick", "ROLE_USER");
        Long candidate1Id = createUser("candidate-snake-pick-1", "Candidate Snake Pick One", "ROLE_USER");
        Long candidate2Id = createUser("candidate-snake-pick-2", "Candidate Snake Pick Two", "ROLE_USER");

        Long sessionId = createSession(owner, "Legacy Snake Pick Session", 2, 45, "SNAKE");
        Long teamAId = createTeam(owner, sessionId, "Snake Red", 1);
        Long teamBId = createTeam(owner, sessionId, "Snake Blue", 2);
        draftService.createCandidate(candidateRequest(sessionId, candidate1Id, "Candidate Snake Pick One", "ZERG"), owner);
        draftService.createCandidate(candidateRequest(sessionId, candidate2Id, "Candidate Snake Pick Two", "TERRAN"), owner);
        draftService.createOrder(orderRequest(sessionId, 1L, teamAId), owner);
        draftService.createOrder(orderRequest(sessionId, 2L, teamBId), owner);
        draftService.createOrder(orderRequest(sessionId, 3L, teamBId), owner);
        draftService.createOrder(orderRequest(sessionId, 4L, teamAId), owner);
        draftService.createOrder(orderRequest(sessionId, 5L, teamAId), owner);

        DraftSessionRequestDto liveAtFifthPick = liveSessionRequest(teamAId);
        liveAtFifthPick.setCurrentPickNo(5);
        draftService.updateSession(sessionId, liveAtFifthPick, owner);

        ResponseDto<DraftPickResponseDto> pickAtFive =
                draftService.createPick(pickRequest(sessionId, 5L, teamAId, candidate1Id, pickerUserId), owner);
        ResponseDto<DraftSessionDetailResponseDto> afterPickAtFive = draftService.getSession(sessionId);
        ResponseDto<DraftPickResponseDto> finalPick =
                draftService.createPick(pickRequest(sessionId, 6L, teamBId, candidate2Id, pickerUserId), owner);
        ResponseDto<DraftSessionDetailResponseDto> afterFinalPick = draftService.getSession(sessionId);

        assertThat(pickAtFive.getStatus()).isEqualTo(200);
        assertThat(afterPickAtFive.getData().getStatus()).isEqualTo("LIVE");
        assertThat(afterPickAtFive.getData().getCurrentPickNo()).isEqualTo(6);
        assertThat(afterPickAtFive.getData().getCurrentDraftTeamId()).isEqualTo(teamBId);
        assertThat(draftOrderRepository.findByDraftSessionIdAndPickNo(sessionId, 6L)).isPresent()
                .get()
                .extracting(DraftOrderEntity::getDraftTeamId)
                .isEqualTo(teamBId);
        assertThat(finalPick.getStatus()).isEqualTo(200);
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

    @Test
    void listHistory_returnsFinishedSessionsWithPagingKeywordAndPickedCount() {
        Long ownerUserId = createUser("history-owner", "History Owner", "ROLE_USER");
        LocalDateTime baseTime = LocalDateTime.of(2026, 5, 11, 20, 0);
        DraftSessionEntity first = createHistorySession(ownerUserId, "May Alpha Draft", "FINISHED", "SNAKE", baseTime);
        DraftSessionEntity second = createHistorySession(ownerUserId, "May Beta Draft", "FINISHED", "BASIC", baseTime.minusHours(1));
        DraftSessionEntity third = createHistorySession(ownerUserId, "May Empty Draft", "FINISHED", "BASIC", baseTime.minusHours(2));
        createHistorySession(ownerUserId, "May Live Draft", "LIVE", "BASIC", null);
        createHistorySession(ownerUserId, "April Finished Draft", "FINISHED", "BASIC", baseTime.minusHours(3));
        createHistoryPick(first, 1L);
        createHistoryPick(first, 2L);
        createHistoryPick(second, 1L);

        ResponseDto<DraftHistoryPageResponseDto> firstPage =
                draftService.listHistory(0, 2, "  may  ");
        ResponseDto<DraftHistoryPageResponseDto> secondPage =
                draftService.listHistory(1, 2, "may");
        ResponseDto<List<DraftSessionSummaryResponseDto>> allSessions = draftService.listSessions();

        assertThat(firstPage.getStatus()).isEqualTo(200);
        assertThat(firstPage.getData().getPage()).isEqualTo(0);
        assertThat(firstPage.getData().getSize()).isEqualTo(2);
        assertThat(firstPage.getData().getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getData().getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getData().isHasNext()).isTrue();
        assertThat(firstPage.getData().isHasPrevious()).isFalse();
        assertThat(firstPage.getData().getItems())
                .extracting("id", "title", "status", "pickedCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first.getId(), "May Alpha Draft", "FINISHED", 2L),
                        org.assertj.core.groups.Tuple.tuple(second.getId(), "May Beta Draft", "FINISHED", 1L)
                );

        assertThat(secondPage.getStatus()).isEqualTo(200);
        assertThat(secondPage.getData().isHasNext()).isFalse();
        assertThat(secondPage.getData().isHasPrevious()).isTrue();
        assertThat(secondPage.getData().getItems())
                .extracting("id", "title", "pickedCount")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(third.getId(), "May Empty Draft", 0L));

        assertThat(allSessions.getStatus()).isEqualTo(200);
        assertThat(allSessions.getData()).filteredOn("id", first.getId())
                .extracting("pickedCount")
                .containsExactly(2L);
    }

    private Long createSession(AuthActor actor, String title, int teamCount, int pickTimeSeconds) {
        return createSession(actor, title, teamCount, pickTimeSeconds, "BASIC");
    }

    private Long createSession(AuthActor actor, String title, int teamCount, int pickTimeSeconds, String orderMode) {
        DraftSessionRequestDto requestDto = sessionRequest(title, teamCount, pickTimeSeconds);
        requestDto.setOrderMode(orderMode);
        return draftService.createSession(requestDto, actor).getData().getId();
    }

    private DraftSessionEntity createHistorySession(
            Long ownerUserId,
            String title,
            String status,
            String orderMode,
            LocalDateTime endedAt
    ) {
        return draftSessionRepository.save(DraftSessionEntity.builder()
                .title(title)
                .ownerUserId(ownerUserId)
                .status(status)
                .orderMode(orderMode)
                .teamCount(2)
                .pickTimeSeconds(45)
                .currentPickNo(1)
                .startedAt(endedAt == null ? null : endedAt.minusHours(1))
                .endedAt(endedAt)
                .build());
    }

    private void createHistoryPick(DraftSessionEntity session, Long pickNo) {
        Long candidateUserId = createUser(
                "history-candidate-" + session.getId() + "-" + pickNo,
                "History Candidate " + session.getId() + "-" + pickNo,
                "ROLE_USER"
        );
        DraftTeamEntity team = draftTeamRepository.save(DraftTeamEntity.builder()
                .draftSessionId(session.getId())
                .teamName("History Team " + session.getId() + "-" + pickNo)
                .displayOrder(pickNo.intValue())
                .pickerUserId(null)
                .build());
        LocalDateTime pickedAt = session.getEndedAt() == null ? LocalDateTime.now() : session.getEndedAt();

        draftCandidateRepository.save(DraftCandidateEntity.builder()
                .draftSessionId(session.getId())
                .candidateUserId(candidateUserId)
                .candidateName("History Candidate " + candidateUserId)
                .race("ZERG")
                .status("PICKED")
                .pickedDraftTeamId(team.getId())
                .pickedAt(pickedAt)
                .build());
        draftPickRepository.save(DraftPickEntity.builder()
                .draftSessionId(session.getId())
                .pickNo(pickNo)
                .draftTeamId(team.getId())
                .candidateUserId(candidateUserId)
                .pickedByUserId(candidateUserId)
                .pickedAt(pickedAt)
                .build());
    }

    private Long createTeam(AuthActor actor, Long sessionId, String teamName, int displayOrder) {
        return draftService.createTeam(teamRequest(sessionId, teamName, displayOrder), actor).getData().getId();
    }

    private DraftSessionRequestDto sessionRequest(String title, int teamCount, int pickTimeSeconds) {
        DraftSessionRequestDto requestDto = new DraftSessionRequestDto();
        requestDto.setTitle(title);
        requestDto.setStatus("READY");
        requestDto.setOrderMode("BASIC");
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

    private Long createLinkedSession(AuthActor owner, Long leagueId, Long teamId, Long pickerUserId) {
        DraftSessionRequestDto request = sessionRequest("Linked Session " + leagueId, 2, 60);
        request.setProleagueId(leagueId);
        request.setTeamCount(null);
        request.setProleagueTeamPickers(proleagueTeamRepository.findAllByLeagueIdOrderByDisplayOrderAscIdAsc(leagueId).stream()
                .map(team -> proleagueTeamPicker(
                        team.getId(),
                        Objects.equals(team.getId(), teamId) ? pickerUserId : team.getLeaderId()
                ))
                .toList());
        return draftService.createSession(request, owner).getData().getId();
    }

    private DraftProleagueTeamPickerRequestDto proleagueTeamPicker(Long proleagueTeamId, Long pickerUserId) {
        DraftProleagueTeamPickerRequestDto request = new DraftProleagueTeamPickerRequestDto();
        request.setProleagueTeamId(proleagueTeamId);
        request.setPickerUserId(pickerUserId);
        return request;
    }

    private Long createProleague(String leagueName) {
        return leagueRepository.save(LeagueEntity.builder()
                .leagueName(leagueName)
                .status(LeagueEntity.STATUS_READY)
                .leagueType(LeagueEntity.TYPE_PROLEAGUE)
                .build()).getId();
    }

    private Long createProleagueTeam(Long leagueId, String teamName, Long leaderId, Long viceLeaderId, int displayOrder) {
        return proleagueTeamRepository.save(ProleagueTeamEntity.builder()
                .leagueId(leagueId)
                .teamName(teamName)
                .leaderId(leaderId)
                .viceLeaderId(viceLeaderId)
                .displayOrder(displayOrder)
                .build()).getId();
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
