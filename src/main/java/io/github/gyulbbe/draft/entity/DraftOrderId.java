package io.github.gyulbbe.draft.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DraftOrderId implements Serializable {
    private Long draftSessionId;
    private Long pickNo;
}
