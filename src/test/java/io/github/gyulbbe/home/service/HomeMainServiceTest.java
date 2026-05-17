package io.github.gyulbbe.home.service;

import io.github.gyulbbe.board.entity.BoardEntity;
import io.github.gyulbbe.board.repository.BoardRepository;
import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.repository.DraftCandidateRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.home.dto.HomeMainResponse;
import io.github.gyulbbe.home.dto.HomeScheduleMatchPlayerResponse;
import io.github.gyulbbe.home.dto.HomeScheduleMatchResponse;
import io.github.gyulbbe.home.dto.HomeScheduleResponse;
import io.github.gyulbbe.tournament.entity.TournamentEntity;
import io.github.gyulbbe.tournament.entity.TournamentStageEntity;
import io.github.gyulbbe.tournament.repository.TournamentGroupRepository;
import io.github.gyulbbe.tournament.repository.TournamentParticipantRepository;
import io.github.gyulbbe.tournament.repository.TournamentRepository;
import io.github.gyulbbe.tournament.repository.TournamentStageRepository;
import io.github.gyulbbe.user.entity.UserEntity;
import io.github.gyulbbe.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeMainServiceTest {

    @Mock
    private HomeScheduleService homeScheduleService;

    @Mock
    private DraftSessionRepository draftSessionRepository;

    @Mock
    private DraftCandidateRepository draftCandidateRepository;

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentStageRepository tournamentStageRepository;

    @Mock
    private TournamentGroupRepository tournamentGroupRepository;

    @Mock
    private TournamentParticipantRepository tournamentParticipantRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HomeMainService homeMainService;

    @Test
    void getHomeMain_splitsSchedulesAndKeepsExistingSections() {
        HomeScheduleResponse notice = schedule(1L, "NOTICE", "공지", null);
        HomeScheduleResponse proleague = schedule(2L, "PROLEAGUE", "프로리그 3라운드", teamPlayMatch());
        HomeScheduleResponse personal = schedule(3L, "PERSONAL_LEAGUE", "개인리그 4강", oneVsOneMatch());
        DraftSessionEntity draft = DraftSessionEntity.builder()
                .id(10L)
                .title("프로리그 드래프트")
                .status("LIVE")
                .teamCount(4)
                .pickTimeSeconds(30)
                .currentPickNo(12)
                .regDate(LocalDateTime.of(2026, 5, 17, 9, 0))
                .updateDate(LocalDateTime.of(2026, 5, 17, 10, 30))
                .build();
        TournamentEntity tournament = TournamentEntity.builder()
                .id(2L)
                .title("개인리그")
                .status(TournamentEntity.STATUS_LIVE)
                .regDate(LocalDateTime.of(2026, 5, 17, 9, 0))
                .updateDate(LocalDateTime.of(2026, 5, 17, 10, 20))
                .build();
        TournamentStageEntity stage = TournamentStageEntity.builder()
                .id(20L)
                .tournamentId(2L)
                .stageNo(1)
                .stageName("본선")
                .stageType(TournamentStageEntity.TYPE_SINGLE_ELIMINATION)
                .displayOrder(1)
                .build();
        BoardEntity board = BoardEntity.builder()
                .id(100L)
                .userId(7L)
                .authorName("ignored")
                .title("오늘 경기 후기")
                .text("경기 내용\n요약")
                .regDate(LocalDateTime.of(2026, 5, 17, 9, 30))
                .build();
        UserEntity author = UserEntity.builder()
                .id(7L)
                .userId("Blackmagic")
                .name("Real Name")
                .build();

        when(homeScheduleService.findPublicSchedules(4)).thenReturn(List.of(notice, proleague, personal));
        when(draftSessionRepository.findHomeMainOngoingSessions(eq(List.of("LIVE", "PAUSED")), any(Pageable.class)))
                .thenReturn(List.of(draft));
        when(draftCandidateRepository.countByDraftSessionId(10L)).thenReturn(28L);
        when(tournamentRepository.findHomeMainLiveTournaments(eq(TournamentEntity.STATUS_LIVE), any(Pageable.class)))
                .thenReturn(List.of(tournament));
        when(tournamentStageRepository.findAllByTournamentIdOrderByDisplayOrderAsc(2L)).thenReturn(List.of(stage));
        when(tournamentGroupRepository.countByStageIdIn(List.of(20L))).thenReturn(1L);
        when(tournamentParticipantRepository.countByTournamentId(2L)).thenReturn(8L);
        when(boardRepository.findTop5ByOrderByRegDateDescIdDesc()).thenReturn(List.of(board));
        when(userRepository.findAllById(List.of(7L))).thenReturn(List.of(author));

        ResponseDto<HomeMainResponse> response = homeMainService.getHomeMain();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getSchedules()).hasSize(3);
        assertThat(response.getData().getNotice().getScheduleGroup()).isEqualTo("NOTICE");
        assertThat(response.getData().getProleagueSchedules()).hasSize(1);
        assertThat(response.getData().getProleagueSchedules().get(0).getMatches().get(0).getMatchFormat()).isEqualTo("2V2");
        assertThat(response.getData().getPersonalLeagueSchedules()).hasSize(1);
        assertThat(response.getData().getPersonalLeagueSchedules().get(0).getMatches().get(0).getMapName()).isEqualTo("Neo Dark Origin");
        assertThat(response.getData().getOngoing()).hasSize(2);
        assertThat(response.getData().getOngoing().get(0).getType()).isEqualTo("DRAFT");
        assertThat(response.getData().getOngoing().get(0).getPrimaryText()).isEqualTo("현재 12픽 진행 중");
        assertThat(response.getData().getOngoing().get(0).getSecondaryText()).isEqualTo("4팀 · 28명");
        assertThat(response.getData().getOngoing().get(1).getPrimaryText()).isEqualTo("싱글 엘리미네이션");
        assertThat(response.getData().getOngoing().get(1).getSecondaryText()).isEqualTo("8명 · 1개 조");
        assertThat(response.getData().getGalleryPosts().get(0).getAuthorUserId()).isEqualTo("Blackmagic");
        assertThat(response.getData().getGalleryPosts().get(0).getSummaryText()).isEqualTo("경기 내용 요약");
        assertThat(response.getData().getBotAlerts()).hasSize(3);
    }

    @Test
    void getHomeMain_keepsSuccessWhenASectionFails() {
        when(homeScheduleService.findPublicSchedules(4)).thenReturn(List.of());
        when(draftSessionRepository.findHomeMainOngoingSessions(eq(List.of("LIVE", "PAUSED")), any(Pageable.class)))
                .thenReturn(List.of());
        when(tournamentRepository.findHomeMainLiveTournaments(eq(TournamentEntity.STATUS_LIVE), any(Pageable.class)))
                .thenReturn(List.of());
        when(boardRepository.findTop5ByOrderByRegDateDescIdDesc()).thenThrow(new RuntimeException("boom"));

        ResponseDto<HomeMainResponse> response = homeMainService.getHomeMain();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getData().getGalleryPosts()).isEmpty();
        assertThat(response.getData().getSchedules()).isEmpty();
        assertThat(response.getData().getProleagueSchedules()).isEmpty();
        assertThat(response.getData().getPersonalLeagueSchedules()).isEmpty();
        assertThat(response.getData().getBotAlerts()).hasSize(1);
        assertThat(response.getData().getBotAlerts().get(0).getType()).isEqualTo("DEFAULT");
    }

    private HomeScheduleResponse schedule(Long id, String group, String title, HomeScheduleMatchResponse match) {
        return HomeScheduleResponse.builder()
                .id(id)
                .scheduleGroup(group)
                .title(title)
                .description(title + " description")
                .scheduledAt(LocalDateTime.of(2026, 5, 17, 20, 0).plusHours(id))
                .timeLabel("20:00")
                .targetUrl("/home/" + id)
                .linkType("DIRECT")
                .navigationUrl("/home/" + id)
                .matches(match == null ? List.of() : List.of(match))
                .build();
    }

    private HomeScheduleMatchResponse oneVsOneMatch() {
        return HomeScheduleMatchResponse.builder()
                .id(10L)
                .displayOrder(1)
                .setLabel("SET 1")
                .matchFormat("1V1")
                .mapId(100L)
                .mapName("Neo Dark Origin")
                .sideAPlayers(List.of(player(1000L, "A", 1, "alpha_snow")))
                .sideBPlayers(List.of(player(1001L, "B", 1, "bravo_mind")))
                .build();
    }

    private HomeScheduleMatchResponse teamPlayMatch() {
        return HomeScheduleMatchResponse.builder()
                .id(20L)
                .displayOrder(2)
                .setLabel("SET 2")
                .matchFormat("2V2")
                .mapId(101L)
                .mapName("Huntress")
                .sideAPlayers(List.of(player(2000L, "A", 1, "alpha_1"), player(2001L, "A", 2, "alpha_2")))
                .sideBPlayers(List.of(player(2002L, "B", 1, "bravo_1"), player(2003L, "B", 2, "bravo_2")))
                .build();
    }

    private HomeScheduleMatchPlayerResponse player(Long id, String side, Integer slotOrder, String playerName) {
        return HomeScheduleMatchPlayerResponse.builder()
                .id(id)
                .side(side)
                .slotOrder(slotOrder)
                .playerName(playerName)
                .build();
    }
}
