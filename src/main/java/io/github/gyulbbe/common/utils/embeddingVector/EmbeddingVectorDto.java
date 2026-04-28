package io.github.gyulbbe.common.utils.embeddingVector;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class EmbeddingVectorDto {
    @NotNull
    private Long referenceId;
    @NotNull
    private String referenceTable;
    @NotNull
    private String text;
    private float[] embeddingVector;
    private Double distance;
}