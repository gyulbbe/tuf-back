package io.github.gyulbbe.common.utils.embeddingVector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingApiRequest {
    private List<String> texts;
}