package io.github.gyulbbe.common.utils.embeddingVector;

import lombok.Data;

import java.util.List;

@Data
public class EmbeddingApiResponse {
    private List<List<Float>> embeddings;
    private int dimension;
    private int count;
}