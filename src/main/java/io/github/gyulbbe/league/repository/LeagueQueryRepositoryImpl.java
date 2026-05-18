package io.github.gyulbbe.league.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.gyulbbe.draft.entity.QDraftSessionEntity;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.QLeagueEntity;
import io.github.gyulbbe.league.entity.QProleagueTeamEntity;
import io.github.gyulbbe.user.entity.QUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LeagueQueryRepositoryImpl implements LeagueQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Long> findAdminLeagueIds(
            String leagueType,
            String keyword,
            String status,
            String linked,
            int page,
            int size
    ) {
        QLeagueEntity league = QLeagueEntity.leagueEntity;
        BooleanExpression condition = adminLeagueCondition(league, leagueType, keyword, status, linked);

        Long totalResult = queryFactory
                .select(league.count())
                .from(league)
                .where(condition)
                .fetchOne();
        long total = totalResult == null ? 0L : totalResult;

        List<Long> ids = queryFactory
                .select(league.id)
                .from(league)
                .where(condition)
                .orderBy(
                        new CaseBuilder()
                                .when(league.status.in(LeagueEntity.STATUS_READY, LeagueEntity.STATUS_LIVE))
                                .then(0)
                                .otherwise(1)
                                .asc(),
                        league.updateDate.desc().nullsLast(),
                        league.id.desc()
                )
                .offset((long) page * size)
                .limit(size)
                .fetch();

        return new PageImpl<>(ids, PageRequest.of(page, size), total);
    }

    @Override
    public Page<Long> findAdminProleagueIds(String keyword, String status, int page, int size) {
        QLeagueEntity league = QLeagueEntity.leagueEntity;
        BooleanExpression condition = adminListCondition(league, keyword, status);
        BooleanExpression proleagueCondition = league.leagueType.eq(LeagueEntity.TYPE_PROLEAGUE);
        condition = condition == null ? proleagueCondition : condition.and(proleagueCondition);

        Long totalResult = queryFactory
                .select(league.count())
                .from(league)
                .where(condition)
                .fetchOne();
        long total = totalResult == null ? 0L : totalResult;

        List<Long> ids = queryFactory
                .select(league.id)
                .from(league)
                .where(condition)
                .orderBy(
                        new CaseBuilder()
                                .when(league.status.in(LeagueEntity.STATUS_READY, LeagueEntity.STATUS_LIVE))
                                .then(0)
                                .otherwise(1)
                                .asc(),
                        league.updateDate.desc().nullsLast(),
                        league.id.desc()
                )
                .offset((long) page * size)
                .limit(size)
                .fetch();

        return new PageImpl<>(ids, PageRequest.of(page, size), total);
    }

    @Override
    public Page<Long> findAdminProleagueHistoryIds(String keyword, LocalDate fromDate, LocalDate toDate, int page, int size) {
        QLeagueEntity league = QLeagueEntity.leagueEntity;
        BooleanExpression condition = historyCondition(league, keyword, fromDate, toDate);

        Long totalResult = queryFactory
                .select(league.count())
                .from(league)
                .where(condition)
                .fetchOne();
        long total = totalResult == null ? 0L : totalResult;

        List<Long> ids = queryFactory
                .select(league.id)
                .from(league)
                .where(condition)
                .orderBy(
                        league.endDate.desc().nullsLast(),
                        league.id.desc()
                )
                .offset((long) page * size)
                .limit(size)
                .fetch();

        return new PageImpl<>(ids, PageRequest.of(page, size), total);
    }

    private BooleanExpression adminListCondition(QLeagueEntity league, String keyword, String status) {
        BooleanExpression condition = null;
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            condition = league.leagueName.containsIgnoreCase(trimmed)
                    .or(league.seasonName.containsIgnoreCase(trimmed));
        }
        if (status != null && !status.isBlank()) {
            BooleanExpression statusCondition = league.status.eq(status.trim().toUpperCase());
            condition = condition == null ? statusCondition : condition.and(statusCondition);
        }
        return condition;
    }

    private BooleanExpression adminLeagueCondition(
            QLeagueEntity league,
            String leagueType,
            String keyword,
            String status,
            String linked
    ) {
        BooleanExpression condition = null;
        if (leagueType != null && !leagueType.isBlank()) {
            condition = league.leagueType.eq(leagueType.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            condition = and(condition, league.leagueName.containsIgnoreCase(trimmed)
                    .or(league.seasonName.containsIgnoreCase(trimmed)));
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = status.trim().toUpperCase();
            BooleanExpression statusCondition = LeagueEntity.STATUS_LIVE.equals(normalizedStatus)
                    ? league.status.in(LeagueEntity.STATUS_READY, LeagueEntity.STATUS_LIVE)
                    : league.status.eq(normalizedStatus);
            condition = and(condition, statusCondition);
        }
        if (linked != null && !linked.isBlank()) {
            BooleanExpression linkedTarget = linkedExpression(league, leagueType);
            String normalizedLinked = linked.trim().toUpperCase();
            if ("LINKED".equals(normalizedLinked)) {
                condition = and(condition, linkedTarget);
            } else if ("UNLINKED".equals(normalizedLinked)) {
                condition = and(condition, linkedTarget.not());
            }
        }
        return condition;
    }

    private BooleanExpression linkedExpression(QLeagueEntity league, String leagueType) {
        QDraftSessionEntity draftSession = QDraftSessionEntity.draftSessionEntity;
        BooleanExpression proleagueLinked = league.draftSessionId.isNotNull()
                .or(JPAExpressions
                        .selectOne()
                        .from(draftSession)
                        .where(draftSession.proleagueId.eq(league.id))
                        .exists());
        BooleanExpression tournamentLinked = league.tournamentId.isNotNull();
        String normalizedType = leagueType == null ? null : leagueType.trim().toUpperCase();
        if (LeagueEntity.TYPE_PROLEAGUE.equals(normalizedType)) {
            return proleagueLinked;
        }
        if (LeagueEntity.TYPE_PERSONAL.equals(normalizedType)
                || LeagueEntity.TYPE_ULTIMATE_BATTLE.equals(normalizedType)
                || LeagueEntity.TYPE_RACE_SURVIVAL.equals(normalizedType)) {
            return tournamentLinked;
        }
        return proleagueLinked.or(tournamentLinked);
    }

    private BooleanExpression and(BooleanExpression left, BooleanExpression right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.and(right);
    }

    private BooleanExpression historyCondition(QLeagueEntity league, String keyword, LocalDate fromDate, LocalDate toDate) {
        BooleanExpression condition = league.status.eq(LeagueEntity.STATUS_FINISHED)
                .and(league.leagueType.eq(LeagueEntity.TYPE_PROLEAGUE));

        if (fromDate != null) {
            condition = condition.and(league.endDate.goe(fromDate));
        }
        if (toDate != null) {
            condition = condition.and(league.endDate.loe(toDate));
        }
        if (keyword == null || keyword.isBlank()) {
            return condition;
        }

        String trimmed = keyword.trim();
        QProleagueTeamEntity team = QProleagueTeamEntity.proleagueTeamEntity;
        QUserEntity leader = new QUserEntity("historyLeader");
        QUserEntity viceLeader = new QUserEntity("historyViceLeader");

        BooleanExpression keywordCondition = league.leagueName.containsIgnoreCase(trimmed)
                .or(league.seasonName.containsIgnoreCase(trimmed))
                .or(JPAExpressions
                        .selectOne()
                        .from(team)
                        .leftJoin(leader).on(leader.id.eq(team.leaderId))
                        .leftJoin(viceLeader).on(viceLeader.id.eq(team.viceLeaderId))
                        .where(
                                team.leagueId.eq(league.id)
                                        .and(leader.userId.containsIgnoreCase(trimmed)
                                                .or(viceLeader.userId.containsIgnoreCase(trimmed)))
                        )
                        .exists());
        return condition.and(keywordCondition);
    }
}
