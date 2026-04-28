package io.github.gyulbbe.common.utils.embeddingVector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingVectorMapper embeddingVectorMapper;
    private final RestTemplate restTemplate;

    @Value("${kure.embedding.url}")
    private String embeddingApiUrl;

    /**
     * 텍스트를 임베딩 벡터로 변환하고 vectors 테이블에 저장
     * @param referenceId 참조 ID
     * @param referenceTable 참조 타입 (예: "commentary", "speech")
     * @param text 임베딩할 텍스트
     * @return 저장된 레코드 수
     */
    public int embedAndSave(Long referenceId, String referenceTable, String text) {
        log.info("임베딩 생성 시작 - referenceId: {}, referenceTable: {}, text: {}", referenceId, referenceTable, text);

        try {
            // 1. Python API를 호출하여 임베딩 벡터 생성
            float[] embeddingVector = getEmbedding(text);

            // 2. DTO 생성
            EmbeddingVectorDto dto = new EmbeddingVectorDto();
            dto.setReferenceId(referenceId);
            dto.setReferenceTable(referenceTable);
            dto.setText(text);
            dto.setEmbeddingVector(embeddingVector);

            // 3. 데이터베이스에 저장
            int result = embeddingVectorMapper.insertEmbeddingVector(dto);
            log.info("임베딩 벡터 저장 완료 - referenceId: {}, dimension: {}", referenceId, embeddingVector.length);

            return result;
        } catch (Exception e) {
            log.error("임베딩 생성 및 저장 실패 - referenceId: {}, error: {}", referenceId, e.getMessage(), e);
            throw new RuntimeException("임베딩 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 여러 텍스트를 한 번에 임베딩하고 저장 (배치 처리)
     * @param dtos 임베딩할 데이터 리스트 (text, referenceId, referenceTable 포함)
     * @return 저장된 레코드 수
     */
    public int embedAndSaveBatch(List<EmbeddingVectorDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            log.warn("임베딩할 데이터가 없습니다.");
            return 0;
        }

        log.info("배치 임베딩 시작 - 텍스트 개수: {}", dtos.size());

        try {
            // 1. 모든 텍스트 추출
            List<String> texts = dtos.stream()
                    .map(EmbeddingVectorDto::getText)
                    .collect(Collectors.toList());

            // 2. Python API를 호출하여 한 번에 임베딩 벡터 생성
            List<float[]> embeddings = getEmbeddings(texts);

            // 3. 각 DTO에 임베딩 벡터 설정 및 저장
            int totalSaved = 0;
            for (int i = 0; i < dtos.size(); i++) {
                EmbeddingVectorDto dto = dtos.get(i);
                dto.setEmbeddingVector(embeddings.get(i));
                totalSaved += embeddingVectorMapper.insertEmbeddingVector(dto);
            }

            log.info("배치 임베딩 완료 - 저장된 레코드 수: {}", totalSaved);
            return totalSaved;

        } catch (Exception e) {
            log.error("배치 임베딩 실패 - error: {}", e.getMessage(), e);
            throw new RuntimeException("배치 임베딩 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Python FastAPI 서버를 호출하여 단일 텍스트의 임베딩 벡터 생성
     * @param text 임베딩할 텍스트
     * @return 임베딩 벡터 (float 배열)
     */
    public float[] getEmbedding(String text) {
        List<float[]> embeddings = getEmbeddings(Collections.singletonList(text));
        return embeddings.get(0);
    }

    /**
     * Python FastAPI 서버를 호출하여 여러 텍스트의 임베딩 벡터 생성
     * @param texts 임베딩할 텍스트 리스트
     * @return 임베딩 벡터 리스트
     */
    public List<float[]> getEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("텍스트 리스트가 비어있습니다.");
        }

        try {
            String apiUrl = embeddingApiUrl + "/embed";
            log.info("Python API 호출 - URL: {}, 텍스트 개수: {}", apiUrl, texts.size());

            // 요청 생성
            EmbeddingApiRequest request = new EmbeddingApiRequest(texts);

            // API 호출
            EmbeddingApiResponse response = restTemplate.postForObject(
                    apiUrl,
                    request,
                    EmbeddingApiResponse.class
            );

            if (response == null || response.getEmbeddings() == null) {
                throw new RuntimeException("Python API 응답이 null입니다.");
            }

            log.info("Python API 응답 - dimension: {}, count: {}", response.getDimension(), response.getCount());

            // List<List<Float>> -> List<float[]> 변환
            return response.getEmbeddings().stream()
                    .map(embedding -> {
                        float[] array = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) {
                            array[i] = embedding.get(i);
                        }
                        return array;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Python API 호출 실패 - error: {}", e.getMessage(), e);
            throw new RuntimeException("임베딩 API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 벡터 검색 - Oracle 23ai VECTOR_DISTANCE 함수 사용
     * @param queryText 검색할 텍스트
     * @param referenceTable 참조 테이블 필터 (null이면 전체 검색)
     * @param topK 반환할 상위 K개 결과
     * @return 유사한 벡터 리스트 (거리 순으로 정렬, 거리가 낮을수록 유사)
     */
    public List<EmbeddingVectorDto> findSimilarVectors(String queryText, String referenceTable, int topK) {
        log.info("유사 벡터 검색 (Oracle VECTOR_DISTANCE) - queryText: {}, referenceTable: {}, topK: {}",
                 queryText, referenceTable, topK);

        try {
            // 1. 쿼리 텍스트를 임베딩 벡터로 변환
            float[] queryVector = getEmbedding(queryText);
            log.debug("쿼리 벡터 생성 완료 - dimension: {}", queryVector.length);

            // 2. 검색 요청 DTO 생성
            SimilaritySearchRequestDto requestDto = new SimilaritySearchRequestDto();
            requestDto.setQueryText(queryText);
            requestDto.setQueryVector(queryVector);
            requestDto.setReferenceTable(referenceTable);
            requestDto.setTopK(topK);

            // 3. Oracle VECTOR_DISTANCE 함수를 사용한 유사도 검색
            List<EmbeddingVectorDto> results = embeddingVectorMapper.findSimilarVectors(requestDto);
            log.info("유사 벡터 검색 완료 - 결과 개수: {}", results.size());

            return results;

        } catch (Exception e) {
            log.error("유사 벡터 검색 실패 - error: {}", e.getMessage(), e);
            throw new RuntimeException("유사 벡터 검색 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 가장 유사한 벡터 하나 조회 - Oracle 23ai VECTOR_DISTANCE 함수 사용
     * @param queryText 검색할 텍스트
     * @param referenceTable 참조 테이블 필터 (null이면 전체 검색)
     * @return 가장 유사한 벡터 (없으면 null)
     */
    public EmbeddingVectorDto findMostSimilarVector(String queryText, String referenceTable) {
        log.info("가장 유사한 벡터 검색 (Oracle VECTOR_DISTANCE) - queryText: {}, referenceTable: {}",
                 queryText, referenceTable);

        try {
            // 1. 쿼리 텍스트를 임베딩 벡터로 변환
            float[] queryVector = getEmbedding(queryText);

            // 2. 검색 요청 DTO 생성
            SimilaritySearchRequestDto requestDto = new SimilaritySearchRequestDto();
            requestDto.setQueryText(queryText);
            requestDto.setQueryVector(queryVector);
            requestDto.setReferenceTable(referenceTable);
            requestDto.setTopK(1);

            // 3. Oracle VECTOR_DISTANCE 함수를 사용한 유사도 검색
            EmbeddingVectorDto result = embeddingVectorMapper.findMostSimilarVector(requestDto);

            if (result != null) {
                log.info("가장 유사한 벡터 검색 완료 - referenceId: {}, distance: {}",
                         result.getReferenceId(), result.getDistance());
            } else {
                log.info("검색 결과 없음");
            }

            return result;

        } catch (Exception e) {
            log.error("가장 유사한 벡터 검색 실패 - error: {}", e.getMessage(), e);
            throw new RuntimeException("가장 유사한 벡터 검색 실패: " + e.getMessage(), e);
        }
    }
}
