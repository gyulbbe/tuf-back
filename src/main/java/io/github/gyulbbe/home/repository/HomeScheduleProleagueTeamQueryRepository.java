package io.github.gyulbbe.home.repository;

import io.github.gyulbbe.home.dto.AdminHomeScheduleProleagueTeamSearchResponse;

import java.util.List;

public interface HomeScheduleProleagueTeamQueryRepository {
    List<AdminHomeScheduleProleagueTeamSearchResponse> searchLiveProleagueTeams(String keyword, int limit);
}
