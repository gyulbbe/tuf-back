package io.github.gyulbbe.league.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
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
    public Page<Long> findAdminProleagueIds(String keyword, String status, int page, int size) {
        QLeagueEntity league = QLeagueEntity.leagueEntity;
        BooleanExpression condition = adminListCondition(league, keyword, status);
        if (condition == null) {
            condition = league.id.isNotNull();
        }

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

    private BooleanExpression historyCondition(QLeagueEntity league, String keyword, LocalDate fromDate, LocalDate toDate) {
        BooleanExpression condition = league.status.eq(LeagueEntity.STATUS_FINISHED);

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
