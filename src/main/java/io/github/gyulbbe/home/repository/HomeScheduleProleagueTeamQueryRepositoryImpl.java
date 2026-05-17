package io.github.gyulbbe.home.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.gyulbbe.home.dto.AdminHomeScheduleProleagueTeamSearchResponse;
import io.github.gyulbbe.league.entity.LeagueEntity;
import io.github.gyulbbe.league.entity.QLeagueEntity;
import io.github.gyulbbe.league.entity.QProleagueTeamEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HomeScheduleProleagueTeamQueryRepositoryImpl implements HomeScheduleProleagueTeamQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AdminHomeScheduleProleagueTeamSearchResponse> searchLiveProleagueTeams(String keyword, int limit) {
        QLeagueEntity league = QLeagueEntity.leagueEntity;
        QProleagueTeamEntity team = QProleagueTeamEntity.proleagueTeamEntity;

        BooleanExpression condition = league.status.eq(LeagueEntity.STATUS_LIVE);
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            condition = condition.and(
                    team.teamName.containsIgnoreCase(trimmed)
                            .or(league.leagueName.containsIgnoreCase(trimmed))
                            .or(league.seasonName.containsIgnoreCase(trimmed))
            );
        }

        return queryFactory
                .select(Projections.constructor(
                        AdminHomeScheduleProleagueTeamSearchResponse.class,
                        team.id,
                        team.teamName,
                        league.id,
                        league.leagueName,
                        league.seasonName
                ))
                .from(team)
                .join(league).on(league.id.eq(team.leagueId))
                .where(condition)
                .orderBy(
                        league.updateDate.desc().nullsLast(),
                        team.displayOrder.asc(),
                        team.id.asc()
                )
                .limit(limit)
                .fetch();
    }
}
