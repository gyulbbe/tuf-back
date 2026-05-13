package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.chat.provider.ChatProviderRouter;
import io.github.gyulbbe.draft.dto.DraftAiAdviceResponseDto;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveCurrentTurnResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveEventType;
import io.github.gyulbbe.draft.dto.DraftLiveRosterItemResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveSessionInfoResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveSnapshotResponseDto;
import io.github.gyulbbe.draft.dto.DraftLiveTeamResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.ws.DraftEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftAiAdviceServiceTest {

    @Mock
    private DraftSnapshotService draftSnapshotService;

    @Mock
    private DraftEventPublisher draftEventPublisher;

    @Mock
    private ChatProviderRouter chatProviderRouter;

    @InjectMocks
    private DraftAiAdviceService draftAiAdviceService;

    @Test
    void does_not_call_ai_before_two_full_rounds() {
        Long sessionId = 10L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        snapshot.getRecentPicks().get(0).setPickNo(4L);
        snapshot.getTeams().get(0).getRoster().get(0).setPickNo(4L);
        snapshot.getCurrentTurn().setPickNo(5L);
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot);

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        assertThat(draftAiAdviceService.hasContext(sessionId)).isTrue();
        assertThat(draftAiAdviceService.lastAppliedPickNo(sessionId)).isEqualTo(4L);
        assertThat(draftAiAdviceService.availableCandidateCount(sessionId)).isEqualTo(2);
        verifyNoInteractions(chatProviderRouter);
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                any(DraftLiveEventType.class),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void does_not_call_ai_when_session_is_finished() {
        Long sessionId = 11L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        snapshot.getSession().setStatus("FINISHED");
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot);

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        verifyNoInteractions(chatProviderRouter);
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                any(DraftLiveEventType.class),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void publishes_pick_review_then_recommendation_after_threshold_pick() {
        Long sessionId = 12L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot, snapshot, snapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("방금 픽은 밸런스를 잘 맞췄다.")
                .thenReturn("다음은 HONEY를 추천한다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        ArgumentCaptor<DraftLiveEventType> typeCaptor = ArgumentCaptor.forClass(DraftLiveEventType.class);
        ArgumentCaptor<DraftAiAdviceResponseDto> adviceCaptor = ArgumentCaptor.forClass(DraftAiAdviceResponseDto.class);
        verify(draftEventPublisher, times(2)).publishAiAdvice(eq(sessionId), typeCaptor.capture(), adviceCaptor.capture());

        assertThat(typeCaptor.getAllValues()).containsExactly(
                DraftLiveEventType.AI_PICK_REVIEW_READY,
                DraftLiveEventType.AI_RECOMMENDATION_READY
        );

        DraftAiAdviceResponseDto review = adviceCaptor.getAllValues().get(0);
        assertThat(review.getPickNo()).isEqualTo(5L);
        assertThat(review.getEvaluatedTeamId()).isEqualTo(100L);
        assertThat(review.getEvaluatedCandidateUserId()).isEqualTo(1000L);
        assertThat(review.getEvaluatedCandidateName()).isEqualTo("FLASH");
        assertThat(review.getNextPickNo()).isNull();
        assertThat(review.getRecommendedCandidateUserId()).isNull();
        assertThat(review.getMessage()).contains("밸런스");

        DraftAiAdviceResponseDto recommendation = adviceCaptor.getAllValues().get(1);
        assertThat(recommendation.getPickNo()).isEqualTo(5L);
        assertThat(recommendation.getNextPickNo()).isEqualTo(6L);
        assertThat(recommendation.getRecommendedTeamId()).isEqualTo(200L);
        assertThat(recommendation.getRecommendedCandidateUserId()).isEqualTo(2000L);
        assertThat(recommendation.getRecommendedCandidateName()).isEqualTo("HONEY");
        assertThat(recommendation.getMessage()).contains("HONEY");
        assertThat(draftAiAdviceService.hasContext(sessionId)).isTrue();
        assertThat(draftAiAdviceService.lastAppliedPickNo(sessionId)).isEqualTo(5L);
        assertThat(draftAiAdviceService.availableCandidateCount(sessionId)).isEqualTo(2);

        InOrder inOrder = inOrder(draftEventPublisher);
        inOrder.verify(draftEventPublisher).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_PICK_REVIEW_READY),
                any(DraftAiAdviceResponseDto.class)
        );
        inOrder.verify(draftEventPublisher).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_RECOMMENDATION_READY),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void ai_prompts_require_display_candidate_names_in_messages() {
        Long sessionId = 19L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot, snapshot, snapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("FLASH를 뽑다니 밸런스가 좋아졌다.")
                .thenReturn("다음은 HONEY를 추천한다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatProviderRouter, times(2)).chat(systemPromptCaptor.capture(), userPromptCaptor.capture());

        assertThat(systemPromptCaptor.getAllValues().get(0))
                .contains("방금 지명한 선수 이름을 반드시 포함")
                .contains("저그는 팀플 운영에 유리")
                .contains("답변은 1문장으로 제한");
        assertThat(userPromptCaptor.getAllValues().get(0))
                .contains("선수: FLASH")
                .contains("방금 지명한 선수 이름(FLASH)을 문장 안에 반드시 포함")
                .contains("저그는 팀플 운영에 유리한 종족")
                .doesNotContain("[전체 팀 종족/티어 현황]");
        assertThat(systemPromptCaptor.getAllValues().get(1))
                .contains("추천 선수 이름을 반드시 포함")
                .contains("저그는 팀플 운영에 유리")
                .contains("답변은 1문장으로 제한");
        assertThat(userPromptCaptor.getAllValues().get(1))
                .contains("추천 선수: HONEY")
                .contains("추천 선수 이름(HONEY)을 문장 안에 반드시 포함")
                .contains("저그는 팀플 운영에 유리한 종족")
                .doesNotContain("[전체 팀 종족/티어 현황]")
                .doesNotContain("[현재 추천 대상]");
    }

    @Test
    void recommendation_prompt_limits_available_candidate_summary_to_five_candidates() {
        Long sessionId = 20L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        snapshot.setAvailableCandidates(List.of(
                candidate(2001L, "CAND1", "1", "TERRAN"),
                candidate(2002L, "CAND2", "1", "TERRAN"),
                candidate(2003L, "CAND3", "1", "TERRAN"),
                candidate(2004L, "CAND4", "1", "TERRAN"),
                candidate(2005L, "CAND5", "1", "TERRAN"),
                candidate(2006L, "CAND6", "1", "TERRAN")
        ));
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot, snapshot, snapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("FLASH를 뽑다니 좋다.")
                .thenReturn("CAND1을 추천한다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatProviderRouter, times(2)).chat(anyString(), userPromptCaptor.capture());

        String recommendationPrompt = userPromptCaptor.getAllValues().get(1);
        assertThat(recommendationPrompt)
                .contains("CAND1")
                .contains("CAND2")
                .contains("CAND3")
                .contains("CAND4")
                .contains("CAND5")
                .doesNotContain("CAND6");
    }

    @Test
    void zerg_candidate_is_not_penalized_like_other_duplicate_races_for_teamplay_value() {
        Long sessionId = 21L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        DraftLiveTeamResponseDto blue = snapshot.getTeams().get(1);
        blue.getRoster().clear();
        blue.getRoster().add(rosterItem(1L, 1201L, "Z1", "2", "ZERG"));
        blue.getRoster().add(rosterItem(2L, 1202L, "Z2", "2", "ZERG"));
        blue.getRoster().add(rosterItem(3L, 1203L, "Z3", "2", "ZERG"));
        blue.getRoster().add(rosterItem(4L, 1204L, "P1", "2", "PROTOSS"));
        blue.getRoster().add(rosterItem(null, 1205L, "P2", "2", "PROTOSS"));
        blue.getRoster().add(rosterItem(null, 1206L, "P3", "2", "PROTOSS"));
        snapshot.setAvailableCandidates(List.of(
                candidate(2000L, "AAA", "1", "PROTOSS"),
                candidate(3000L, "ZZZ", "1", "ZERG")
        ));
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot, snapshot, snapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("FLASH를 뽑다니 좋다.")
                .thenReturn("ZZZ를 추천한다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        ArgumentCaptor<DraftAiAdviceResponseDto> adviceCaptor = ArgumentCaptor.forClass(DraftAiAdviceResponseDto.class);
        verify(draftEventPublisher, times(2)).publishAiAdvice(
                eq(sessionId),
                any(DraftLiveEventType.class),
                adviceCaptor.capture()
        );

        DraftAiAdviceResponseDto recommendation = adviceCaptor.getAllValues().get(1);
        assertThat(recommendation.getRecommendedCandidateName()).isEqualTo("ZZZ");
    }

    @Test
    void reuses_existing_context_and_applies_next_pick_incrementally() {
        Long sessionId = 1L;
        DraftLiveSnapshotResponseDto firstPickSnapshot = eligibleSnapshot();
        DraftLiveSnapshotResponseDto secondPickSnapshot = nextPickSnapshot();
        when(draftSnapshotService.getBroadcastSnapshot(sessionId))
                .thenReturn(
                        firstPickSnapshot,
                        firstPickSnapshot,
                        firstPickSnapshot,
                        secondPickSnapshot,
                        secondPickSnapshot,
                        secondPickSnapshot
                );
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("첫 평가")
                .thenReturn("첫 추천")
                .thenReturn("둘째 평가")
                .thenReturn("둘째 추천");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);
        int firstContextIdentity = draftAiAdviceService.contextIdentityHash(sessionId);

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        assertThat(firstContextIdentity).isNotZero();
        assertThat(draftAiAdviceService.contextIdentityHash(sessionId)).isEqualTo(firstContextIdentity);
        assertThat(draftAiAdviceService.lastAppliedPickNo(sessionId)).isEqualTo(6L);
        assertThat(draftAiAdviceService.availableCandidateCount(sessionId)).isEqualTo(1);
        verify(draftSnapshotService, times(6)).getBroadcastSnapshot(sessionId);
        verify(draftEventPublisher, times(4)).publishAiAdvice(
                eq(sessionId),
                any(DraftLiveEventType.class),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void does_not_publish_recommendation_when_turn_changed_after_review() {
        Long sessionId = 13L;
        DraftLiveSnapshotResponseDto initialSnapshot = eligibleSnapshot();
        DraftLiveSnapshotResponseDto changedSnapshot = eligibleSnapshot();
        changedSnapshot.getCurrentTurn().setPickNo(7L);
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(initialSnapshot, changedSnapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("방금 픽은 괜찮았다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        verify(chatProviderRouter, times(1)).chat(anyString(), anyString());
        verify(draftEventPublisher).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_PICK_REVIEW_READY),
                any(DraftAiAdviceResponseDto.class)
        );
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_RECOMMENDATION_READY),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void does_not_publish_recommendation_when_turn_changes_while_recommendation_is_generating() {
        Long sessionId = 18L;
        DraftLiveSnapshotResponseDto initialSnapshot = eligibleSnapshot();
        DraftLiveSnapshotResponseDto recommendationStartSnapshot = eligibleSnapshot();
        DraftLiveSnapshotResponseDto changedSnapshot = eligibleSnapshot();
        changedSnapshot.getCurrentTurn().setPickNo(7L);
        when(draftSnapshotService.getBroadcastSnapshot(sessionId))
                .thenReturn(initialSnapshot, recommendationStartSnapshot, changedSnapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("방금 픽은 괜찮았다.")
                .thenReturn("다음은 HONEY를 추천한다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        verify(chatProviderRouter, times(2)).chat(anyString(), anyString());
        verify(draftEventPublisher).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_PICK_REVIEW_READY),
                any(DraftAiAdviceResponseDto.class)
        );
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_RECOMMENDATION_READY),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void does_not_publish_recommendation_when_latest_snapshot_is_finished() {
        Long sessionId = 14L;
        DraftLiveSnapshotResponseDto initialSnapshot = eligibleSnapshot();
        DraftLiveSnapshotResponseDto finishedSnapshot = eligibleSnapshot();
        finishedSnapshot.getSession().setStatus("FINISHED");
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(initialSnapshot, finishedSnapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("방금 픽은 괜찮았다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        verify(chatProviderRouter, times(1)).chat(anyString(), anyString());
        verify(draftEventPublisher).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_PICK_REVIEW_READY),
                any(DraftAiAdviceResponseDto.class)
        );
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_RECOMMENDATION_READY),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void does_not_publish_recommendation_when_latest_snapshot_has_no_available_candidates() {
        Long sessionId = 15L;
        DraftLiveSnapshotResponseDto initialSnapshot = eligibleSnapshot();
        DraftLiveSnapshotResponseDto latestSnapshot = eligibleSnapshot();
        latestSnapshot.setAvailableCandidates(List.of());
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(initialSnapshot, latestSnapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenReturn("방금 픽은 괜찮았다.");

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        verify(chatProviderRouter, times(1)).chat(anyString(), anyString());
        verify(draftEventPublisher).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_PICK_REVIEW_READY),
                any(DraftAiAdviceResponseDto.class)
        );
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                eq(DraftLiveEventType.AI_RECOMMENDATION_READY),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void does_not_publish_any_ai_event_when_pick_review_ai_fails() {
        Long sessionId = 16L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot);
        when(chatProviderRouter.chat(anyString(), anyString()))
                .thenThrow(new RuntimeException("ai down"));

        draftAiAdviceService.generateAndPublishAdvice(sessionId);

        verify(chatProviderRouter, times(1)).chat(anyString(), anyString());
        verify(draftEventPublisher, never()).publishAiAdvice(
                eq(sessionId),
                any(DraftLiveEventType.class),
                any(DraftAiAdviceResponseDto.class)
        );
    }

    @Test
    void evict_context_removes_cached_draft_context() {
        Long sessionId = 17L;
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        snapshot.getRecentPicks().get(0).setPickNo(4L);
        snapshot.getTeams().get(0).getRoster().get(0).setPickNo(4L);
        snapshot.getCurrentTurn().setPickNo(5L);
        when(draftSnapshotService.getBroadcastSnapshot(sessionId)).thenReturn(snapshot);

        draftAiAdviceService.generateAndPublishAdvice(sessionId);
        draftAiAdviceService.evictContext(sessionId);

        assertThat(draftAiAdviceService.hasContext(sessionId)).isFalse();
    }

    private DraftLiveSnapshotResponseDto eligibleSnapshot() {
        DraftLiveSessionInfoResponseDto session = new DraftLiveSessionInfoResponseDto();
        session.setId(1L);
        session.setStatus("LIVE");
        session.setTeamCount(2);

        DraftLiveCurrentTurnResponseDto currentTurn = new DraftLiveCurrentTurnResponseDto();
        currentTurn.setPickNo(6L);
        currentTurn.setTeamId(200L);
        currentTurn.setTeamName("Blue");

        DraftPickResponseDto justPicked = new DraftPickResponseDto();
        justPicked.setPickNo(5L);
        justPicked.setDraftTeamId(100L);
        justPicked.setDraftTeamName("Red");
        justPicked.setCandidateUserId(1000L);
        justPicked.setCandidateUserLoginId("FLASH");
        justPicked.setCandidateName("FLASH");
        justPicked.setTier("1");
        justPicked.setRace("TERRAN");

        DraftLiveTeamResponseDto red = new DraftLiveTeamResponseDto();
        red.setId(100L);
        red.setTeamName("Red");
        red.getRoster().add(rosterItem(5L, 1000L, "FLASH", "1", "TERRAN"));

        DraftLiveTeamResponseDto blue = new DraftLiveTeamResponseDto();
        blue.setId(200L);
        blue.setTeamName("Blue");
        blue.getRoster().add(rosterItem(2L, 1200L, "BISU", "1", "PROTOSS"));

        DraftCandidateResponseDto honey = candidate(2000L, "HONEY", "1", "ZERG");
        DraftCandidateResponseDto hon = candidate(3000L, "HON", "4", "TERRAN");

        DraftLiveSnapshotResponseDto snapshot = new DraftLiveSnapshotResponseDto();
        snapshot.setSession(session);
        snapshot.setCurrentTurn(currentTurn);
        snapshot.setTeams(List.of(red, blue));
        snapshot.setRecentPicks(List.of(justPicked));
        snapshot.setAvailableCandidates(List.of(hon, honey));
        return snapshot;
    }

    private DraftLiveSnapshotResponseDto nextPickSnapshot() {
        DraftLiveSnapshotResponseDto snapshot = eligibleSnapshot();
        snapshot.getCurrentTurn().setPickNo(7L);
        snapshot.getCurrentTurn().setTeamId(100L);
        snapshot.getCurrentTurn().setTeamName("Red");

        DraftPickResponseDto justPicked = snapshot.getRecentPicks().get(0);
        justPicked.setPickNo(6L);
        justPicked.setDraftTeamId(200L);
        justPicked.setDraftTeamName("Blue");
        justPicked.setCandidateUserId(2000L);
        justPicked.setCandidateUserLoginId("HONEY");
        justPicked.setCandidateName("HONEY");
        justPicked.setTier("1");
        justPicked.setRace("ZERG");

        snapshot.getTeams().get(1).getRoster().add(rosterItem(6L, 2000L, "HONEY", "1", "ZERG"));
        snapshot.setAvailableCandidates(List.of(candidate(3000L, "HON", "4", "TERRAN")));
        return snapshot;
    }

    private DraftCandidateResponseDto candidate(Long userId, String loginId, String tier, String race) {
        DraftCandidateResponseDto candidate = new DraftCandidateResponseDto();
        candidate.setCandidateUserId(userId);
        candidate.setCandidateUserLoginId(loginId);
        candidate.setCandidateName(loginId);
        candidate.setTier(tier);
        candidate.setRace(race);
        candidate.setStatus("WAITING");
        return candidate;
    }

    private DraftLiveRosterItemResponseDto rosterItem(Long pickNo, Long userId, String loginId, String tier, String race) {
        DraftLiveRosterItemResponseDto rosterItem = new DraftLiveRosterItemResponseDto();
        rosterItem.setPickNo(pickNo);
        rosterItem.setCandidateUserId(userId);
        rosterItem.setCandidateUserLoginId(loginId);
        rosterItem.setCandidateName(loginId);
        rosterItem.setTier(tier);
        rosterItem.setRace(race);
        return rosterItem;
    }
}
