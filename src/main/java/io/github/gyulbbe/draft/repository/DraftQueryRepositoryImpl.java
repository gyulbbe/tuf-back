package io.github.gyulbbe.draft.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.gyulbbe.draft.dto.*;
import io.github.gyulbbe.draft.entity.*;
import io.github.gyulbbe.user.entity.QUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DraftQueryRepositoryImpl implements DraftQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<DraftSessionSummaryResponseDto> findSessionSummary(Long sessionId) {
        QDraftSessionEntity draftSession = QDraftSessionEntity.draftSessionEntity;

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftSessionSummaryResponseDto.class,
                                draftSession.id,
                                draftSession.title,
                                draftSession.status,
                                draftSession.teamCount,
                                draftSession.pickTimeSeconds,
                                draftSession.draftMode,
                                draftSession.currentPickNo,
                                draftSession.currentDraftTeamId,
                                draftSession.deadlineAt,
                                draftSession.startedAt,
                                draftSession.endedAt
                        ))
                        .from(draftSession)
                        .where(draftSession.id.eq(sessionId))
                        .fetchOne()
        );
    }

    @Override
    public List<DraftSessionSummaryResponseDto> findSessionSummaries() {
        QDraftSessionEntity draftSession = QDraftSessionEntity.draftSessionEntity;

        return queryFactory
                .select(Projections.bean(
                        DraftSessionSummaryResponseDto.class,
                        draftSession.id,
                        draftSession.title,
                        draftSession.status,
                        draftSession.teamCount,
                        draftSession.pickTimeSeconds,
                        draftSession.draftMode,
                        draftSession.currentPickNo,
                        draftSession.currentDraftTeamId,
                        draftSession.deadlineAt,
                        draftSession.startedAt,
                        draftSession.endedAt
                ))
                .from(draftSession)
                .orderBy(draftSession.id.desc())
                .fetch();
    }

    @Override
    public Optional<DraftTeamResponseDto> findTeam(Long teamId) {
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;
        QUserEntity picker = new QUserEntity("draftTeamPicker");

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftTeamResponseDto.class,
                                draftTeam.id,
                                draftTeam.draftSessionId,
                                draftTeam.teamName,
                                draftTeam.displayOrder,
                                draftTeam.pickerUserId,
                                picker.name.as("pickerName")
                        ))
                        .from(draftTeam)
                        .leftJoin(picker).on(picker.id.eq(draftTeam.pickerUserId))
                        .where(draftTeam.id.eq(teamId))
                        .fetchOne()
        );
    }

    @Override
    public List<DraftTeamResponseDto> findTeamsBySessionId(Long sessionId) {
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;
        QUserEntity picker = new QUserEntity("draftTeamPicker");

        return queryFactory
                .select(Projections.bean(
                        DraftTeamResponseDto.class,
                        draftTeam.id,
                        draftTeam.draftSessionId,
                        draftTeam.teamName,
                        draftTeam.displayOrder,
                        draftTeam.pickerUserId,
                        picker.name.as("pickerName")
                ))
                .from(draftTeam)
                .leftJoin(picker).on(picker.id.eq(draftTeam.pickerUserId))
                .where(draftTeam.draftSessionId.eq(sessionId))
                .orderBy(draftTeam.displayOrder.asc(), draftTeam.id.asc())
                .fetch();
    }

    @Override
    public List<DraftCandidateResponseDto> findCandidatesBySessionId(Long sessionId) {
        QDraftCandidateEntity draftCandidate = QDraftCandidateEntity.draftCandidateEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;

        return queryFactory
                .select(Projections.bean(
                        DraftCandidateResponseDto.class,
                        draftCandidate.draftSessionId,
                        draftCandidate.candidateUserId,
                        draftCandidate.candidateName,
                        draftCandidate.race,
                        draftCandidate.status,
                        draftCandidate.pickedDraftTeamId,
                        draftTeam.teamName.as("pickedDraftTeamName"),
                        draftCandidate.pickedAt
                ))
                .from(draftCandidate)
                .leftJoin(draftTeam)
                .on(
                        draftTeam.id.eq(draftCandidate.pickedDraftTeamId)
                                .and(draftTeam.draftSessionId.eq(draftCandidate.draftSessionId))
                )
                .where(draftCandidate.draftSessionId.eq(sessionId))
                .orderBy(draftCandidate.candidateName.asc(), draftCandidate.candidateUserId.asc())
                .fetch();
    }

    @Override
    public Optional<DraftCandidateResponseDto> findCandidate(Long sessionId, Long candidateUserId) {
        QDraftCandidateEntity draftCandidate = QDraftCandidateEntity.draftCandidateEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftCandidateResponseDto.class,
                                draftCandidate.draftSessionId,
                                draftCandidate.candidateUserId,
                                draftCandidate.candidateName,
                                draftCandidate.race,
                                draftCandidate.status,
                                draftCandidate.pickedDraftTeamId,
                                draftTeam.teamName.as("pickedDraftTeamName"),
                                draftCandidate.pickedAt
                        ))
                        .from(draftCandidate)
                        .leftJoin(draftTeam)
                        .on(
                                draftTeam.id.eq(draftCandidate.pickedDraftTeamId)
                                        .and(draftTeam.draftSessionId.eq(draftCandidate.draftSessionId))
                        )
                        .where(
                                draftCandidate.draftSessionId.eq(sessionId),
                                draftCandidate.candidateUserId.eq(candidateUserId)
                        )
                        .fetchOne()
        );
    }

    @Override
    public List<DraftOrderResponseDto> findOrdersBySessionId(Long sessionId) {
        QDraftOrderEntity draftOrder = QDraftOrderEntity.draftOrderEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;

        return queryFactory
                .select(Projections.bean(
                        DraftOrderResponseDto.class,
                        draftOrder.draftSessionId,
                        draftOrder.pickNo,
                        draftOrder.draftTeamId,
                        draftTeam.teamName.as("draftTeamName")
                ))
                .from(draftOrder)
                .join(draftTeam)
                .on(
                        draftTeam.id.eq(draftOrder.draftTeamId)
                                .and(draftTeam.draftSessionId.eq(draftOrder.draftSessionId))
                )
                .where(draftOrder.draftSessionId.eq(sessionId))
                .orderBy(draftOrder.pickNo.asc())
                .fetch();
    }

    @Override
    public Optional<DraftOrderResponseDto> findOrder(Long sessionId, Long pickNo) {
        QDraftOrderEntity draftOrder = QDraftOrderEntity.draftOrderEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftOrderResponseDto.class,
                                draftOrder.draftSessionId,
                                draftOrder.pickNo,
                                draftOrder.draftTeamId,
                                draftTeam.teamName.as("draftTeamName")
                        ))
                        .from(draftOrder)
                        .join(draftTeam)
                        .on(
                                draftTeam.id.eq(draftOrder.draftTeamId)
                                        .and(draftTeam.draftSessionId.eq(draftOrder.draftSessionId))
                        )
                        .where(draftOrder.draftSessionId.eq(sessionId), draftOrder.pickNo.eq(pickNo))
                        .fetchOne()
        );
    }

    @Override
    public List<DraftPickResponseDto> findPicksBySessionId(Long sessionId) {
        QDraftPickEntity draftPick = QDraftPickEntity.draftPickEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;
        QDraftCandidateEntity draftCandidate = QDraftCandidateEntity.draftCandidateEntity;
        QUserEntity user = QUserEntity.userEntity;

        return queryFactory
                .select(Projections.bean(
                        DraftPickResponseDto.class,
                        draftPick.draftSessionId,
                        draftPick.pickNo,
                        draftPick.draftTeamId,
                        draftTeam.teamName.as("draftTeamName"),
                        draftPick.candidateUserId,
                        draftCandidate.candidateName,
                        draftPick.pickedByUserId,
                        user.name.as("pickedByUserName"),
                        draftPick.pickedAt
                ))
                .from(draftPick)
                .join(draftTeam)
                .on(
                        draftTeam.id.eq(draftPick.draftTeamId)
                                .and(draftTeam.draftSessionId.eq(draftPick.draftSessionId))
                )
                .join(draftCandidate)
                .on(
                        draftCandidate.draftSessionId.eq(draftPick.draftSessionId)
                                .and(draftCandidate.candidateUserId.eq(draftPick.candidateUserId))
                )
                .leftJoin(user).on(user.id.eq(draftPick.pickedByUserId))
                .where(draftPick.draftSessionId.eq(sessionId))
                .orderBy(draftPick.pickNo.asc())
                .fetch();
    }

    @Override
    public Optional<DraftPickResponseDto> findPick(Long sessionId, Long pickNo) {
        QDraftPickEntity draftPick = QDraftPickEntity.draftPickEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;
        QDraftCandidateEntity draftCandidate = QDraftCandidateEntity.draftCandidateEntity;
        QUserEntity user = QUserEntity.userEntity;

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftPickResponseDto.class,
                                draftPick.draftSessionId,
                                draftPick.pickNo,
                                draftPick.draftTeamId,
                                draftTeam.teamName.as("draftTeamName"),
                                draftPick.candidateUserId,
                                draftCandidate.candidateName,
                                draftPick.pickedByUserId,
                                user.name.as("pickedByUserName"),
                                draftPick.pickedAt
                        ))
                        .from(draftPick)
                        .join(draftTeam)
                        .on(
                                draftTeam.id.eq(draftPick.draftTeamId)
                                        .and(draftTeam.draftSessionId.eq(draftPick.draftSessionId))
                        )
                        .join(draftCandidate)
                        .on(
                                draftCandidate.draftSessionId.eq(draftPick.draftSessionId)
                                        .and(draftCandidate.candidateUserId.eq(draftPick.candidateUserId))
                        )
                        .leftJoin(user).on(user.id.eq(draftPick.pickedByUserId))
                        .where(draftPick.draftSessionId.eq(sessionId), draftPick.pickNo.eq(pickNo))
                        .fetchOne()
        );
    }
}
