package io.github.gyulbbe.league.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProleagueHistoryCleanupRepository {

    private final EntityManager entityManager;

    public int deleteCommentariesByLeagueId(Long leagueId) {
        return entityManager.createNativeQuery("""
                        DELETE FROM COMMENTARIES
                        WHERE MATCH_INFO_ID IN (
                            SELECT ID FROM MATCH_INFOS WHERE LEAGUE_ID = :leagueId
                        )
                        """)
                .setParameter("leagueId", leagueId)
                .executeUpdate();
    }

    public int deleteMatchPlayersByLeagueId(Long leagueId) {
        return entityManager.createNativeQuery("""
                        DELETE FROM MATCH_PLAYERS
                        WHERE MATCH_INFO_ID IN (
                            SELECT ID FROM MATCH_INFOS WHERE LEAGUE_ID = :leagueId
                        )
                        """)
                .setParameter("leagueId", leagueId)
                .executeUpdate();
    }

    public int deleteMatchInfosByLeagueId(Long leagueId) {
        return entityManager.createNativeQuery("""
                        DELETE FROM MATCH_INFOS
                        WHERE LEAGUE_ID = :leagueId
                        """)
                .setParameter("leagueId", leagueId)
                .executeUpdate();
    }

    public int deleteSeriesInfosByLeagueId(Long leagueId) {
        return entityManager.createNativeQuery("""
                        DELETE FROM SERIES_INFOS
                        WHERE LEAGUE_ID = :leagueId
                        """)
                .setParameter("leagueId", leagueId)
                .executeUpdate();
    }
}
