package io.github.gyulbbe.ai.mapper;

import io.github.gyulbbe.ai.dto.LeagueRecordSummaryDto;
import io.github.gyulbbe.ai.dto.UserLeagueRecordDto;
import io.github.gyulbbe.ai.dto.UserMatchResultDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiRecordMapper {
    List<UserLeagueRecordDto> findAllUserLeagueRecords();

    List<UserLeagueRecordDto> findUserLeagueRecordsByLoginIds(@Param("loginIds") List<String> loginIds);

    List<UserMatchResultDto> findAllUserMatchResults();

    List<LeagueRecordSummaryDto> findLeagueRecordSummaries();
}
