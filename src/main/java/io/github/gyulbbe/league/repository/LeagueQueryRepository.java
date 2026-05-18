package io.github.gyulbbe.league.repository;

import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface LeagueQueryRepository {
    Page<Long> findAdminLeagueIds(
            String leagueType,
            String keyword,
            String status,
            String linked,
            int page,
            int size
    );

    Page<Long> findAdminProleagueIds(String keyword, String status, int page, int size);

    Page<Long> findAdminProleagueHistoryIds(String keyword, LocalDate fromDate, LocalDate toDate, int page, int size);
}
