package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftCandidateRequestDto {
    private Long candidateUserId;
    private String candidateName;
    private String race;
}
