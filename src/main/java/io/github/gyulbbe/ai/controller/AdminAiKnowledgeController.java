package io.github.gyulbbe.ai.controller;

import io.github.gyulbbe.ai.dto.AiKnowledgeRebuildResponseDto;
import io.github.gyulbbe.ai.service.AiKnowledgeDocumentService;
import io.github.gyulbbe.common.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/ai-knowledge")
public class AdminAiKnowledgeController {

    private final AiKnowledgeDocumentService aiKnowledgeDocumentService;

    @PostMapping("/records/rebuild")
    public ResponseEntity<ResponseDto<AiKnowledgeRebuildResponseDto>> rebuildRecordDocuments() {
        return respond(ResponseDto.success(aiKnowledgeDocumentService.rebuildRecordDocuments()));
    }
}
