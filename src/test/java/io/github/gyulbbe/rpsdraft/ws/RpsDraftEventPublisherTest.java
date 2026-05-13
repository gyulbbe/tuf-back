package io.github.gyulbbe.rpsdraft.ws;

import io.github.gyulbbe.rpsdraft.auth.RpsDraftActor;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveEventType;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveRosterItemResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSessionInfoResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveSnapshotResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftLiveTeamResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickResponseDto;
import io.github.gyulbbe.rpsdraft.service.RpsDraftSnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RpsDraftEventPublisherTest {

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    @Mock
    private RpsDraftSnapshotService rpsDraftSnapshotService;

    @InjectMocks
    private RpsDraftEventPublisher rpsDraftEventPublisher;

    @Test
    void state_change_events_always_include_broadcast_snapshot() {
        RpsDraftActor actor = new RpsDraftActor(1L, "picker01", "ROLE_USER");

        for (RpsDraftLiveEventType type : RpsDraftLiveEventType.values()) {
            reset(simpMessagingTemplate, rpsDraftSnapshotService);
            RpsDraftLiveSnapshotResponseDto snapshot = populatedSnapshot();
            when(rpsDraftSnapshotService.getBroadcastSnapshot(10L)).thenReturn(snapshot);

            rpsDraftEventPublisher.publish(10L, type, actor, "message", "round-result");

            ArgumentCaptor<RpsDraftLiveEventResponseDto> eventCaptor =
                    ArgumentCaptor.forClass(RpsDraftLiveEventResponseDto.class);
            verify(simpMessagingTemplate).convertAndSend(eq("/topic/rps-drafts/10"), eventCaptor.capture());

            RpsDraftLiveEventResponseDto event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo(type);
            assertThat(event.getSnapshot()).isSameAs(snapshot);
            assertThat(event.getSnapshot().getPermissions()).isNull();
            assertThat(event.getSnapshot().getSession().getOwnerUserLoginId()).isEqualTo("owner-login");
            assertThat(event.getSnapshot().getTeams()).extracting("pickerUserLoginId").containsExactly("picker-login");
            assertThat(event.getSnapshot().getAvailableCandidates()).extracting("candidateUserLoginId").containsExactly("available-login");
            assertThat(event.getSnapshot().getPickedCandidates()).extracting("candidateUserLoginId").containsExactly("picked-login");
            assertThat(event.getSnapshot().getRecentPicks()).extracting("candidateUserLoginId").containsExactly("picked-login");
            assertThat(event.getSnapshot().getTeams().get(0).getRoster()).extracting("candidateUserLoginId").containsExactly("picked-login");
        }
    }

    @Test
    void state_change_event_is_not_published_without_snapshot() {
        when(rpsDraftSnapshotService.getBroadcastSnapshot(10L)).thenReturn(null);

        assertThatThrownBy(() -> rpsDraftEventPublisher.publish(
                10L,
                RpsDraftLiveEventType.PICK_COMPLETED,
                new RpsDraftActor(1L, "picker01", "ROLE_USER"),
                "message",
                null
        )).hasMessageContaining("snapshot is required");

        verify(simpMessagingTemplate, never()).convertAndSend(eq("/topic/rps-drafts/10"), any(Object.class));
    }

    private RpsDraftLiveSnapshotResponseDto populatedSnapshot() {
        RpsDraftLiveSessionInfoResponseDto session = new RpsDraftLiveSessionInfoResponseDto();
        session.setOwnerUserId(1L);
        session.setOwnerUserLoginId("owner-login");
        session.setOwnerName("owner-login");

        RpsDraftLiveRosterItemResponseDto rosterItem = new RpsDraftLiveRosterItemResponseDto();
        rosterItem.setCandidateUserId(3L);
        rosterItem.setCandidateUserLoginId("picked-login");
        rosterItem.setCandidateName("picked-login");
        rosterItem.setTier("1");
        rosterItem.setRace("ZERG");

        RpsDraftLiveTeamResponseDto team = new RpsDraftLiveTeamResponseDto();
        team.setPickerUserId(2L);
        team.setPickerUserLoginId("picker-login");
        team.setPickerName("picker-login");
        team.setRoster(List.of(rosterItem));

        RpsDraftCandidateResponseDto availableCandidate = new RpsDraftCandidateResponseDto();
        availableCandidate.setCandidateUserId(4L);
        availableCandidate.setCandidateUserLoginId("available-login");
        availableCandidate.setCandidateName("available-login");
        availableCandidate.setTier("2");
        availableCandidate.setRace("TERRAN");

        RpsDraftCandidateResponseDto pickedCandidate = new RpsDraftCandidateResponseDto();
        pickedCandidate.setCandidateUserId(3L);
        pickedCandidate.setCandidateUserLoginId("picked-login");
        pickedCandidate.setCandidateName("picked-login");
        pickedCandidate.setTier("1");
        pickedCandidate.setRace("ZERG");

        RpsDraftPickResponseDto recentPick = new RpsDraftPickResponseDto();
        recentPick.setCandidateUserId(3L);
        recentPick.setCandidateUserLoginId("picked-login");
        recentPick.setCandidateName("picked-login");
        recentPick.setTier("1");
        recentPick.setRace("ZERG");
        recentPick.setPickedByUserId(2L);
        recentPick.setPickedByUserLoginId("picker-login");
        recentPick.setPickedByUserName("picker-login");

        RpsDraftLiveSnapshotResponseDto snapshot = new RpsDraftLiveSnapshotResponseDto();
        snapshot.setSession(session);
        snapshot.setTeams(List.of(team));
        snapshot.setAvailableCandidates(List.of(availableCandidate));
        snapshot.setPickedCandidates(List.of(pickedCandidate));
        snapshot.setRecentPicks(List.of(recentPick));
        snapshot.setPermissions(null);
        return snapshot;
    }
}
