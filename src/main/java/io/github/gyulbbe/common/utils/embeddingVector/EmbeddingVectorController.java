package io.github.gyulbbe.common.utils.embeddingVector;

import io.github.gyulbbe.common.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

import static io.github.gyulbbe.common.web.ApiResponses.respond;

@Slf4j
@RestController
@RequestMapping("/api/vector")
@RequiredArgsConstructor
public class EmbeddingVectorController {

    private final EmbeddingService embeddingService;

    /**
     * 유사도 검색 API (Oracle 23ai VECTOR_DISTANCE 사용)
     */
    @PostMapping("/search")
    public ResponseEntity<ResponseDto<List<EmbeddingVectorDto>>> searchSimilarVectors(
            @Valid @RequestBody SimilaritySearchRequestDto requestDto) {
        try {
            List<EmbeddingVectorDto> results = embeddingService.findSimilarVectors(
                    requestDto.getQueryText(),
                    requestDto.getReferenceTable(),
                    requestDto.getTopK()
            );
            return respond(ResponseDto.success(results));
        } catch (Exception e) {
            log.error("유사도 검색 실패", e);
            return respond(ResponseDto.fail("유사도 검색 실패: " + e.getMessage()));
        }
    }

    /**
     * 가장 유사한 벡터 하나 조회 API
     */
    @PostMapping("/search/top")
    public ResponseEntity<ResponseDto<EmbeddingVectorDto>> findMostSimilarVector(
            @RequestParam String queryText,
            @RequestParam(required = false) String referenceTable) {
        try {
            EmbeddingVectorDto result = embeddingService.findMostSimilarVector(queryText, referenceTable);
            if (result != null) {
                return respond(ResponseDto.success(result));
            } else {
                return respond(ResponseDto.fail("검색 결과 없음"));
            }
        } catch (Exception e) {
            log.error("가장 유사한 벡터 검색 실패", e);
            return respond(ResponseDto.fail("검색 실패: " + e.getMessage()));
        }
    }

    /**
     * 임베딩 생성 및 저장 API
     */
    @PostMapping("/embed")
    public ResponseEntity<ResponseDto<String>> embedAndSave(
            @RequestParam Long referenceId,
            @RequestParam String referenceTable,
            @RequestParam String text) {
        try {
            int result = embeddingService.embedAndSave(referenceId, referenceTable, text);
            if (result > 0) {
                return respond(ResponseDto.success("임베딩 저장 완료"));
            } else {
                return respond(ResponseDto.fail("임베딩 저장 실패"));
            }
        } catch (Exception e) {
            log.error("임베딩 저장 실패", e);
            return respond(ResponseDto.fail("임베딩 저장 실패: " + e.getMessage()));
        }
    }

    /**
     * 배치 임베딩 API
     */
    @PostMapping("/embed/batch")
    public ResponseEntity<ResponseDto<String>> embedBatch(
            @RequestBody List<EmbeddingVectorDto> dtos) {
        try {
            int result = embeddingService.embedAndSaveBatch(dtos);
            String message = String.format("배치 임베딩 완료 - 저장된 레코드 수: %d", result);
            return respond(ResponseDto.success(message));
        } catch (Exception e) {
            log.error("배치 임베딩 실패", e);
            return respond(ResponseDto.fail("배치 임베딩 실패: " + e.getMessage()));
        }
    }

}
