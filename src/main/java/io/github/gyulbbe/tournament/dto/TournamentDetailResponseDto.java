package io.github.gyulbbe.tournament.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TournamentDetailResponseDto {
    private Long id;
    private String title;
    private String bracketType;
    private String status;
    private int groupCount;
    private int participantCount;
    private LocalDateTime regDate;
    private LocalDateTime updateDate;
    private List<TournamentParticipantResponseDto> participants;
    private List<TournamentStageResponseDto> stages;
    private List<TournamentGroupResponseDto> groups;
}
