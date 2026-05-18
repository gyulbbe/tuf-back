package io.github.gyulbbe.draft.repository;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.gyulbbe.draft.dto.DraftCandidateResponseDto;
import io.github.gyulbbe.draft.dto.DraftHistoryPageResponseDto;
import io.github.gyulbbe.draft.dto.DraftOrderResponseDto;
import io.github.gyulbbe.draft.dto.DraftPickResponseDto;
import io.github.gyulbbe.draft.dto.DraftSessionSummaryResponseDto;
import io.github.gyulbbe.draft.dto.DraftTeamResponseDto;
import io.github.gyulbbe.draft.entity.QDraftCandidateEntity;
import io.github.gyulbbe.draft.entity.QDraftOrderEntity;
import io.github.gyulbbe.draft.entity.QDraftPickEntity;
import io.github.gyulbbe.draft.entity.QDraftSessionEntity;
import io.github.gyulbbe.draft.entity.QDraftTeamEntity;
import io.github.gyulbbe.league.entity.QLeagueEntity;
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
        QUserEntity owner = new QUserEntity("draftSessionOwner");
        QLeagueEntity proleague = new QLeagueEntity("draftSessionProleague");

        DraftSessionSummaryResponseDto summary = queryFactory
                .select(Projections.bean(
                        DraftSessionSummaryResponseDto.class,
                        draftSession.id,
                        draftSession.title,
                        draftSession.ownerUserId,
                        owner.userId.as("ownerUserLoginId"),
                        owner.userId.as("ownerName"),
                        draftSession.proleagueId,
                        proleague.leagueName.as("proleagueName"),
                        draftSession.status,
                        draftSession.orderMode,
                        draftSession.teamCount,
                        draftSession.pickTimeSeconds,
                        pickedCountProjection(draftSession),
                        draftSession.currentPickNo,
                        draftSession.currentDraftTeamId,
                        draftSession.deadlineAt,
                        draftSession.startedAt,
                        draftSession.endedAt
                ))
                .from(draftSession)
                .leftJoin(owner).on(owner.id.eq(draftSession.ownerUserId))
                .leftJoin(proleague).on(proleague.id.eq(draftSession.proleagueId))
                .where(draftSession.id.eq(sessionId))
                .fetchOne();
        return Optional.ofNullable(summary);
    }

    @Override
    public List<DraftSessionSummaryResponseDto> findSessionSummaries() {
        QDraftSessionEntity draftSession = QDraftSessionEntity.draftSessionEntity;
        QUserEntity owner = new QUserEntity("draftSessionOwner");
        QLeagueEntity proleague = new QLeagueEntity("draftSessionProleague");

        return queryFactory
                .select(Projections.bean(
                        DraftSessionSummaryResponseDto.class,
                        draftSession.id,
                        draftSession.title,
                        draftSession.ownerUserId,
                        owner.userId.as("ownerUserLoginId"),
                        owner.userId.as("ownerName"),
                        draftSession.proleagueId,
                        proleague.leagueName.as("proleagueName"),
                        draftSession.status,
                        draftSession.orderMode,
                        draftSession.teamCount,
                        draftSession.pickTimeSeconds,
                        pickedCountProjection(draftSession),
                        draftSession.currentPickNo,
                        draftSession.currentDraftTeamId,
                        draftSession.deadlineAt,
                        draftSession.startedAt,
                        draftSession.endedAt
                ))
                .from(draftSession)
                .leftJoin(owner).on(owner.id.eq(draftSession.ownerUserId))
                .leftJoin(proleague).on(proleague.id.eq(draftSession.proleagueId))
                .orderBy(draftSession.id.desc())
                .fetch();
    }

    @Override
    public List<DraftSessionSummaryResponseDto> findSessionSummariesByProleagueId(Long proleagueId) {
        QDraftSessionEntity draftSession = QDraftSessionEntity.draftSessionEntity;
        QUserEntity owner = new QUserEntity("draftSessionOwner");
        QLeagueEntity proleague = new QLeagueEntity("draftSessionProleague");

        return queryFactory
                .select(Projections.bean(
                        DraftSessionSummaryResponseDto.class,
                        draftSession.id,
                        draftSession.title,
                        draftSession.ownerUserId,
                        owner.userId.as("ownerUserLoginId"),
                        owner.userId.as("ownerName"),
                        draftSession.proleagueId,
                        proleague.leagueName.as("proleagueName"),
                        draftSession.status,
                        draftSession.orderMode,
                        draftSession.teamCount,
                        draftSession.pickTimeSeconds,
                        pickedCountProjection(draftSession),
                        draftSession.currentPickNo,
                        draftSession.currentDraftTeamId,
                        draftSession.deadlineAt,
                        draftSession.startedAt,
                        draftSession.endedAt
                ))
                .from(draftSession)
                .leftJoin(owner).on(owner.id.eq(draftSession.ownerUserId))
                .leftJoin(proleague).on(proleague.id.eq(draftSession.proleagueId))
                .where(draftSession.proleagueId.eq(proleagueId))
                .orderBy(draftSession.id.desc())
                .fetch();
    }

    @Override
    public DraftHistoryPageResponseDto findFinishedSessionHistory(String keyword, int page, int size) {
        QDraftSessionEntity draftSession = QDraftSessionEntity.draftSessionEntity;
        QUserEntity owner = new QUserEntity("draftSessionHistoryOwner");
        QLeagueEntity proleague = new QLeagueEntity("draftSessionHistoryProleague");

        BooleanExpression condition = historyCondition(draftSession, keyword);
        Long totalElementsResult = queryFactory
                .select(draftSession.count())
                .from(draftSession)
                .where(condition)
                .fetchOne();
        long totalElements = totalElementsResult == null ? 0L : totalElementsResult;

        List<DraftSessionSummaryResponseDto> items = queryFactory
                .select(Projections.bean(
                        DraftSessionSummaryResponseDto.class,
                        draftSession.id,
                        draftSession.title,
                        draftSession.ownerUserId,
                        owner.userId.as("ownerUserLoginId"),
                        owner.userId.as("ownerName"),
                        draftSession.proleagueId,
                        proleague.leagueName.as("proleagueName"),
                        draftSession.status,
                        draftSession.orderMode,
                        draftSession.teamCount,
                        draftSession.pickTimeSeconds,
                        pickedCountProjection(draftSession),
                        draftSession.currentPickNo,
                        draftSession.currentDraftTeamId,
                        draftSession.deadlineAt,
                        draftSession.startedAt,
                        draftSession.endedAt
                ))
                .from(draftSession)
                .leftJoin(owner).on(owner.id.eq(draftSession.ownerUserId))
                .leftJoin(proleague).on(proleague.id.eq(draftSession.proleagueId))
                .where(condition)
                .orderBy(draftSession.endedAt.desc().nullsLast(), draftSession.id.desc())
                .offset((long) page * size)
                .limit(size)
                .fetch();

        DraftHistoryPageResponseDto response = new DraftHistoryPageResponseDto();
        response.setItems(items);
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(totalElements);
        response.setTotalPages(calculateTotalPages(totalElements, size));
        response.setHasNext(page + 1 < response.getTotalPages());
        response.setHasPrevious(page > 0);
        return response;
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
                                draftTeam.proleagueTeamId,
                                draftTeam.teamName,
                                draftTeam.displayOrder,
                                draftTeam.pickerUserId,
                                picker.userId.as("pickerUserLoginId"),
                                picker.userId.as("pickerName")
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
                        draftTeam.proleagueTeamId,
                        draftTeam.teamName,
                        draftTeam.displayOrder,
                        draftTeam.pickerUserId,
                        picker.userId.as("pickerUserLoginId"),
                        picker.userId.as("pickerName")
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
        QUserEntity candidateUser = new QUserEntity("draftCandidateUser");

        return queryFactory
                .select(Projections.bean(
                        DraftCandidateResponseDto.class,
                        draftCandidate.draftSessionId,
                        draftCandidate.candidateUserId,
                        candidateUser.userId.as("candidateUserLoginId"),
                        candidateUser.userId.as("candidateName"),
                        candidateUser.tier,
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
                .leftJoin(candidateUser).on(candidateUser.id.eq(draftCandidate.candidateUserId))
                .where(draftCandidate.draftSessionId.eq(sessionId))
                .orderBy(candidateUser.userId.asc(), draftCandidate.candidateUserId.asc())
                .fetch();
    }

    @Override
    public Optional<DraftCandidateResponseDto> findCandidate(Long sessionId, Long candidateUserId) {
        QDraftCandidateEntity draftCandidate = QDraftCandidateEntity.draftCandidateEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;
        QUserEntity candidateUser = new QUserEntity("draftCandidateUser");

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftCandidateResponseDto.class,
                                draftCandidate.draftSessionId,
                                draftCandidate.candidateUserId,
                                candidateUser.userId.as("candidateUserLoginId"),
                                candidateUser.userId.as("candidateName"),
                                candidateUser.tier,
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
                        .leftJoin(candidateUser).on(candidateUser.id.eq(draftCandidate.candidateUserId))
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
        QUserEntity candidateUser = new QUserEntity("draftPickCandidateUser");
        QUserEntity pickedByUser = new QUserEntity("draftPickPickedByUser");

        return queryFactory
                .select(Projections.bean(
                        DraftPickResponseDto.class,
                        draftPick.draftSessionId,
                        draftPick.pickNo,
                        draftPick.draftTeamId,
                        draftTeam.teamName.as("draftTeamName"),
                        draftPick.candidateUserId,
                        candidateUser.userId.as("candidateUserLoginId"),
                        candidateUser.userId.as("candidateName"),
                        candidateUser.tier,
                        draftCandidate.race,
                        draftPick.pickedByUserId,
                        pickedByUser.userId.as("pickedByUserLoginId"),
                        pickedByUser.userId.as("pickedByUserName"),
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
                .leftJoin(candidateUser).on(candidateUser.id.eq(draftPick.candidateUserId))
                .leftJoin(pickedByUser).on(pickedByUser.id.eq(draftPick.pickedByUserId))
                .where(draftPick.draftSessionId.eq(sessionId))
                .orderBy(draftPick.pickNo.asc())
                .fetch();
    }

    @Override
    public Optional<DraftPickResponseDto> findPick(Long sessionId, Long pickNo) {
        QDraftPickEntity draftPick = QDraftPickEntity.draftPickEntity;
        QDraftTeamEntity draftTeam = QDraftTeamEntity.draftTeamEntity;
        QDraftCandidateEntity draftCandidate = QDraftCandidateEntity.draftCandidateEntity;
        QUserEntity candidateUser = new QUserEntity("draftPickCandidateUser");
        QUserEntity pickedByUser = new QUserEntity("draftPickPickedByUser");

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                DraftPickResponseDto.class,
                                draftPick.draftSessionId,
                                draftPick.pickNo,
                                draftPick.draftTeamId,
                                draftTeam.teamName.as("draftTeamName"),
                                draftPick.candidateUserId,
                                candidateUser.userId.as("candidateUserLoginId"),
                                candidateUser.userId.as("candidateName"),
                                candidateUser.tier,
                                draftCandidate.race,
                                draftPick.pickedByUserId,
                                pickedByUser.userId.as("pickedByUserLoginId"),
                                pickedByUser.userId.as("pickedByUserName"),
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
                        .leftJoin(candidateUser).on(candidateUser.id.eq(draftPick.candidateUserId))
                        .leftJoin(pickedByUser).on(pickedByUser.id.eq(draftPick.pickedByUserId))
                        .where(draftPick.draftSessionId.eq(sessionId), draftPick.pickNo.eq(pickNo))
                        .fetchOne()
        );
    }

    private BooleanExpression historyCondition(QDraftSessionEntity draftSession, String keyword) {
        BooleanExpression condition = draftSession.status.eq("FINISHED");
        if (keyword != null && !keyword.isBlank()) {
            condition = condition.and(draftSession.title.containsIgnoreCase(keyword));
        }
        return condition;
    }

    private com.querydsl.core.types.Expression<Long> pickedCountProjection(QDraftSessionEntity draftSession) {
        QDraftPickEntity draftPick = new QDraftPickEntity("draftSessionPickedCount");
        return ExpressionUtils.as(
                JPAExpressions
                        .select(draftPick.count())
                        .from(draftPick)
                        .where(draftPick.draftSessionId.eq(draftSession.id)),
                "pickedCount"
        );
    }

    private int calculateTotalPages(long totalElements, int size) {
        if (totalElements <= 0) {
            return 0;
        }
        return Math.toIntExact((totalElements + size - 1) / size);
    }
}
