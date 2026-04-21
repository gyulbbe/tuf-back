package io.github.gyulbbe.rpsdraft.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftCandidateResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftPickResponseDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftSessionQueryDto;
import io.github.gyulbbe.rpsdraft.dto.RpsDraftTeamResponseDto;
import io.github.gyulbbe.rpsdraft.entity.QRpsDraftCandidateEntity;
import io.github.gyulbbe.rpsdraft.entity.QRpsDraftPickEntity;
import io.github.gyulbbe.rpsdraft.entity.QRpsDraftSessionEntity;
import io.github.gyulbbe.rpsdraft.entity.QRpsDraftTeamEntity;
import io.github.gyulbbe.user.entity.QUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RpsDraftQueryRepositoryImpl implements RpsDraftQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<RpsDraftSessionQueryDto> findSession(Long sessionId) {
        QRpsDraftSessionEntity session = QRpsDraftSessionEntity.rpsDraftSessionEntity;
        QUserEntity owner = new QUserEntity("rpsDraftOwner");

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                RpsDraftSessionQueryDto.class,
                                session.id,
                                session.title,
                                session.ownerUserId,
                                owner.name.as("ownerName"),
                                session.status,
                                session.currentPickNo,
                                session.currentDraftTeamId,
                                session.pendingDraftTeamId,
                                session.team1RpsChoice,
                                session.team2RpsChoice,
                                session.rpsResult,
                                session.startedAt,
                                session.endedAt
                        ))
                        .from(session)
                        .leftJoin(owner).on(owner.id.eq(session.ownerUserId))
                        .where(session.id.eq(sessionId))
                        .fetchOne()
        );
    }

    @Override
    public List<RpsDraftSessionQueryDto> findSessions() {
        QRpsDraftSessionEntity session = QRpsDraftSessionEntity.rpsDraftSessionEntity;
        QUserEntity owner = new QUserEntity("rpsDraftOwner");

        return queryFactory
                .select(Projections.bean(
                        RpsDraftSessionQueryDto.class,
                        session.id,
                        session.title,
                        session.ownerUserId,
                        owner.name.as("ownerName"),
                        session.status,
                        session.currentPickNo,
                        session.currentDraftTeamId,
                        session.pendingDraftTeamId,
                        session.team1RpsChoice,
                        session.team2RpsChoice,
                        session.rpsResult,
                        session.startedAt,
                        session.endedAt
                ))
                .from(session)
                .leftJoin(owner).on(owner.id.eq(session.ownerUserId))
                .orderBy(session.id.desc())
                .fetch();
    }

    @Override
    public List<RpsDraftTeamResponseDto> findTeamsBySessionId(Long sessionId) {
        QRpsDraftTeamEntity team = QRpsDraftTeamEntity.rpsDraftTeamEntity;
        QUserEntity picker = new QUserEntity("rpsDraftPicker");

        return queryFactory
                .select(Projections.bean(
                        RpsDraftTeamResponseDto.class,
                        team.id,
                        team.rpsDraftSessionId,
                        team.teamName,
                        team.displayOrder,
                        team.pickerUserId,
                        picker.name.as("pickerName")
                ))
                .from(team)
                .leftJoin(picker).on(picker.id.eq(team.pickerUserId))
                .where(team.rpsDraftSessionId.eq(sessionId))
                .orderBy(team.displayOrder.asc(), team.id.asc())
                .fetch();
    }

    @Override
    public Optional<RpsDraftTeamResponseDto> findTeam(Long teamId) {
        QRpsDraftTeamEntity team = QRpsDraftTeamEntity.rpsDraftTeamEntity;
        QUserEntity picker = new QUserEntity("rpsDraftPicker");

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                RpsDraftTeamResponseDto.class,
                                team.id,
                                team.rpsDraftSessionId,
                                team.teamName,
                                team.displayOrder,
                                team.pickerUserId,
                                picker.name.as("pickerName")
                        ))
                        .from(team)
                        .leftJoin(picker).on(picker.id.eq(team.pickerUserId))
                        .where(team.id.eq(teamId))
                        .fetchOne()
        );
    }

    @Override
    public List<RpsDraftCandidateResponseDto> findCandidatesBySessionId(Long sessionId) {
        QRpsDraftCandidateEntity candidate = QRpsDraftCandidateEntity.rpsDraftCandidateEntity;
        QRpsDraftTeamEntity team = QRpsDraftTeamEntity.rpsDraftTeamEntity;

        return queryFactory
                .select(Projections.bean(
                        RpsDraftCandidateResponseDto.class,
                        candidate.rpsDraftSessionId,
                        candidate.candidateUserId,
                        candidate.candidateName,
                        candidate.race,
                        candidate.status,
                        candidate.pickedRpsDraftTeamId,
                        team.teamName.as("pickedRpsDraftTeamName"),
                        candidate.pickedAt
                ))
                .from(candidate)
                .leftJoin(team)
                .on(
                        team.id.eq(candidate.pickedRpsDraftTeamId)
                                .and(team.rpsDraftSessionId.eq(candidate.rpsDraftSessionId))
                )
                .where(candidate.rpsDraftSessionId.eq(sessionId))
                .orderBy(candidate.candidateName.asc(), candidate.candidateUserId.asc())
                .fetch();
    }

    @Override
    public Optional<RpsDraftCandidateResponseDto> findCandidate(Long sessionId, Long candidateUserId) {
        QRpsDraftCandidateEntity candidate = QRpsDraftCandidateEntity.rpsDraftCandidateEntity;
        QRpsDraftTeamEntity team = QRpsDraftTeamEntity.rpsDraftTeamEntity;

        return Optional.ofNullable(
                queryFactory
                        .select(Projections.bean(
                                RpsDraftCandidateResponseDto.class,
                                candidate.rpsDraftSessionId,
                                candidate.candidateUserId,
                                candidate.candidateName,
                                candidate.race,
                                candidate.status,
                                candidate.pickedRpsDraftTeamId,
                                team.teamName.as("pickedRpsDraftTeamName"),
                                candidate.pickedAt
                        ))
                        .from(candidate)
                        .leftJoin(team)
                        .on(
                                team.id.eq(candidate.pickedRpsDraftTeamId)
                                        .and(team.rpsDraftSessionId.eq(candidate.rpsDraftSessionId))
                        )
                        .where(
                                candidate.rpsDraftSessionId.eq(sessionId),
                                candidate.candidateUserId.eq(candidateUserId)
                        )
                        .fetchOne()
        );
    }

    @Override
    public List<RpsDraftPickResponseDto> findPicksBySessionId(Long sessionId) {
        QRpsDraftPickEntity pick = QRpsDraftPickEntity.rpsDraftPickEntity;
        QRpsDraftTeamEntity team = QRpsDraftTeamEntity.rpsDraftTeamEntity;
        QRpsDraftCandidateEntity candidate = QRpsDraftCandidateEntity.rpsDraftCandidateEntity;
        QUserEntity pickedBy = new QUserEntity("rpsDraftPickedBy");

        return queryFactory
                .select(Projections.bean(
                        RpsDraftPickResponseDto.class,
                        pick.rpsDraftSessionId,
                        pick.pickNo,
                        pick.rpsDraftTeamId,
                        team.teamName.as("rpsDraftTeamName"),
                        pick.candidateUserId,
                        candidate.candidateName,
                        pick.pickedByUserId,
                        pickedBy.name.as("pickedByUserName"),
                        pick.pickedAt
                ))
                .from(pick)
                .join(team)
                .on(
                        team.id.eq(pick.rpsDraftTeamId)
                                .and(team.rpsDraftSessionId.eq(pick.rpsDraftSessionId))
                )
                .join(candidate)
                .on(
                        candidate.rpsDraftSessionId.eq(pick.rpsDraftSessionId)
                                .and(candidate.candidateUserId.eq(pick.candidateUserId))
                )
                .leftJoin(pickedBy).on(pickedBy.id.eq(pick.pickedByUserId))
                .where(pick.rpsDraftSessionId.eq(sessionId))
                .orderBy(pick.pickNo.asc())
                .fetch();
    }
}
