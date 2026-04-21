package io.github.gyulbbe.rpsdraft.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RpsDraftCandidateId implements Serializable {
    private Long rpsDraftSessionId;
    private Long candidateUserId;
}
