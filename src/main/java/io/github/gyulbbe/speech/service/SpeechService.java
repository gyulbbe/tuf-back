package io.github.gyulbbe.speech.service;

import io.github.gyulbbe.common.dto.ResponseDto;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingService;
import io.github.gyulbbe.speech.dto.SpeechDto;
import io.github.gyulbbe.speech.entity.SpeechEntity;
import io.github.gyulbbe.speech.repository.SpeechRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SpeechService {

    private final SpeechRepository speechLearningRepository;
    private final EmbeddingService embeddingService;

    /**
     * 채팅 리스트 저장 (임베딩 포함)
     * @param dtoList 채팅 데이터 리스트
     * @return 저장된 엔티티 개수
     */
    public ResponseDto<String> insertSpeechLearningList(List<SpeechDto> dtoList) {
        try {
            if (dtoList == null || dtoList.isEmpty()) {
                return ResponseDto.fail("저장할 채팅 데이터가 없습니다.");
            }

            log.info("채팅 리스트 저장 시작 - 개수: {}", dtoList.size());

            // 1. 모든 채팅 텍스트 추출
            List<String> chatTexts = dtoList.stream()
                    .map(SpeechDto::getChat)
                    .filter(chat -> chat != null && !chat.isEmpty())
                    .toList();

            // 2. 배치로 임베딩 생성
            List<float[]> embeddings = embeddingService.getEmbeddings(chatTexts);
            log.info("임베딩 생성 완료 - 개수: {}", embeddings.size());

            // 3. 엔티티 생성 및 저장
            List<SpeechEntity> entities = new ArrayList<>();
            for (int i = 0; i < dtoList.size(); i++) {
                SpeechDto dto = dtoList.get(i);
                SpeechEntity entity = SpeechEntity.builder()
                        .nickname(dto.getNickname())
                        .chat(dto.getChat())
                        .chatEmbeddingVector(
                                (dto.getChat() != null && !dto.getChat().isEmpty())
                                        ? embeddings.get(i) : null
                        )
                        .build();

                entities.add(entity);
            }

            List<SpeechEntity> savedEntities = speechLearningRepository.saveAll(entities);
            log.info("채팅 리스트 저장 완료 - 저장된 개수: {}", savedEntities.size());

            String message = String.format("채팅 %d개가 성공적으로 저장되었습니다.", savedEntities.size());
            return ResponseDto.success(message);
        } catch (Exception e) {
            log.error("채팅 리스트 저장 실패", e);
            return ResponseDto.fail("채팅 리스트 저장에 실패했습니다: " + e.getMessage());
        }
    }
}
