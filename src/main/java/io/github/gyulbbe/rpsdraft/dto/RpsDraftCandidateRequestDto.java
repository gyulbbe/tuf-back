package io.github.gyulbbe.rpsdraft.dto;

import lombok.Data;

@Data
public class RpsDraftCandidateRequestDto {
    private Long candidateUserId;

    @Deprecated
    private String candidateName;

    private String race;
}
