package io.github.gyulbbe.draft.service;

import io.github.gyulbbe.draft.entity.DraftPickEntity;
import io.github.gyulbbe.draft.entity.DraftSessionEntity;
import io.github.gyulbbe.draft.entity.DraftTeamEntity;
import io.github.gyulbbe.draft.repository.DraftPickRepository;
import io.github.gyulbbe.draft.repository.DraftSessionRepository;
import io.github.gyulbbe.draft.repository.DraftTeamRepository;
import io.github.gyulbbe.league.entity.ProleagueTeamMemberEntity;
import io.github.gyulbbe.league.repository.ProleagueTeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProleagueDraftRosterSyncService {

    private final DraftSessionRepository draftSessionRepository;
    private final DraftTeamRepository draftTeamRepository;
    private final DraftPickRepository draftPickRepository;
    private final ProleagueTeamMemberRepository proleagueTeamMemberRepository;

    public void syncIfLinked(DraftSessionEntity session) {
        if (session == null || session.getProleagueId() == null) {
            return;
        }
        sync(session.getId(), session.getProleagueId());
    }

    public void syncIfLinked(Long draftSessionId) {
        if (draftSessionId == null) {
            return;
        }
        draftSessionRepository.findById(draftSessionId).ifPresent(this::syncIfLinked);
    }

    private void sync(Long draftSessionId, Long leagueId) {
        Map<Long, DraftTeamEntity> teamsById = draftTeamRepository.findAllByDraftSessionId(draftSessionId).stream()
                .collect(Collectors.toMap(DraftTeamEntity::getId, team -> team));
        List<DraftPickEntity> picks = draftPickRepository.findAllByDraftSessionIdOrderByPickNoAsc(draftSessionId);

        for (DraftPickEntity pick : picks) {
            DraftTeamEntity team = teamsById.get(pick.getDraftTeamId());
            if (team == null || team.getProleagueTeamId() == null) {
                throw new IllegalStateException("Linked draft pick does not map to a proleague team.");
            }
            if (proleagueTeamMemberRepository.existsActiveConflict(
                    leagueId,
                    pick.getCandidateUserId(),
                    ProleagueTeamMemberEntity.STATUS_ACTIVE,
                    draftSessionId
            )) {
                throw new IllegalStateException("Picked candidate is already an active proleague team member.");
            }
        }

        proleagueTeamMemberRepository.deleteByLeagueIdAndSourceAndSourceDraftSessionId(
                leagueId,
                ProleagueTeamMemberEntity.SOURCE_DRAFT,
                draftSessionId
        );

        List<ProleagueTeamMemberEntity> members = picks.stream()
                .map(pick -> {
                    DraftTeamEntity team = teamsById.get(pick.getDraftTeamId());
                    return ProleagueTeamMemberEntity.builder()
                            .leagueId(leagueId)
                            .proleagueTeamId(team.getProleagueTeamId())
                            .userId(pick.getCandidateUserId())
                            .source(ProleagueTeamMemberEntity.SOURCE_DRAFT)
                            .sourceDraftSessionId(draftSessionId)
                            .draftPickNo(pick.getPickNo())
                            .displayOrder(pick.getPickNo() == null ? 1 : pick.getPickNo().intValue())
                            .status(ProleagueTeamMemberEntity.STATUS_ACTIVE)
                            .build();
                })
                .toList();
        proleagueTeamMemberRepository.saveAll(members);
    }
}
