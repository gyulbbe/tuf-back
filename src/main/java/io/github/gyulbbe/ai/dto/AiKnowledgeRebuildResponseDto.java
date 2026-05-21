package io.github.gyulbbe.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiKnowledgeRebuildResponseDto {
    private int deletedDocuments;
    private int createdDocuments;
    private int embeddedVectors;
}
