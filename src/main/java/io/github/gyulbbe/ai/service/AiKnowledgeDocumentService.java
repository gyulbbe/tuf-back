package io.github.gyulbbe.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gyulbbe.ai.dto.AiKnowledgeRebuildResponseDto;
import io.github.gyulbbe.ai.dto.LeagueRecordSummaryDto;
import io.github.gyulbbe.ai.dto.UserLeagueRecordDto;
import io.github.gyulbbe.ai.dto.UserMatchResultDto;
import io.github.gyulbbe.ai.entity.AiKnowledgeDocumentEntity;
import io.github.gyulbbe.ai.mapper.AiRecordMapper;
import io.github.gyulbbe.ai.repository.AiKnowledgeDocumentRepository;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingService;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingVectorDto;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingVectorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiKnowledgeDocumentService {

    private static final List<String> RECORD_DOCUMENT_TYPES = List.of(
            AiKnowledgeDocumentEntity.TYPE_USER_LEAGUE_RECORD_SUMMARY,
            AiKnowledgeDocumentEntity.TYPE_MATCH_RESULT_SUMMARY,
            AiKnowledgeDocumentEntity.TYPE_LEAGUE_RECORD_SUMMARY
    );
    private static final int MAX_CONTENT_LENGTH = 3900;

    private final AiKnowledgeDocumentRepository documentRepository;
    private final AiRecordMapper aiRecordMapper;
    private final EmbeddingService embeddingService;
    private final EmbeddingVectorMapper embeddingVectorMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiKnowledgeRebuildResponseDto rebuildRecordDocuments() {
        List<AiKnowledgeDocumentEntity> existing = documentRepository.findAllByDocumentTypeIn(RECORD_DOCUMENT_TYPES);
        List<Long> existingIds = existing.stream()
                .map(AiKnowledgeDocumentEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!existingIds.isEmpty()) {
            embeddingVectorMapper.deleteByReferenceTableAndReferenceIds(
                    AiKnowledgeDocumentEntity.TABLE_NAME,
                    existingIds
            );
            documentRepository.deleteAllInBatch(existing);
        }

        List<AiKnowledgeDocumentEntity> documents = new ArrayList<>();
        aiRecordMapper.findAllUserLeagueRecords().forEach(record -> documents.add(toUserLeagueRecordDocument(record)));
        aiRecordMapper.findAllUserMatchResults().forEach(result -> documents.add(toMatchResultDocument(result)));
        aiRecordMapper.findLeagueRecordSummaries().forEach(summary -> documents.add(toLeagueRecordSummaryDocument(summary)));

        List<AiKnowledgeDocumentEntity> savedDocuments = documentRepository.saveAllAndFlush(documents);
        int embedded = embedDocuments(savedDocuments);

        return new AiKnowledgeRebuildResponseDto(existing.size(), savedDocuments.size(), embedded);
    }

    private int embedDocuments(List<AiKnowledgeDocumentEntity> documents) {
        if (documents.isEmpty()) {
            return 0;
        }
        List<EmbeddingVectorDto> vectors = documents.stream()
                .map(document -> {
                    EmbeddingVectorDto dto = new EmbeddingVectorDto();
                    dto.setReferenceId(document.getId());
                    dto.setReferenceTable(AiKnowledgeDocumentEntity.TABLE_NAME);
                    dto.setChunkIndex(0);
                    dto.setText(document.getContent());
                    dto.setMetadata(document.getMetadata());
                    return dto;
                })
                .toList();
        return embeddingService.embedAndSaveBatch(vectors);
    }

    private AiKnowledgeDocumentEntity toUserLeagueRecordDocument(UserLeagueRecordDto record) {
        String title = safe(record.getLoginId()) + " " + safe(record.getLeagueName()) + " 전적 요약";
        String content = limit("""
                전적 요약: userId=%s, 리그=%s, 유형=%s.
                공식 경기 전적은 %d승 %d패 %d무, 점수 합계는 %d득점 %d실점이다.
                기준 데이터는 관리자 승인 완료된 공식 경기 결과만 포함한다.
                데이터 출처는 %s이다.
                """.formatted(
                safe(record.getLoginId()),
                safe(record.getLeagueName()),
                safe(record.getLeagueType()),
                value(record.getWins()),
                value(record.getLosses()),
                value(record.getDraws()),
                value(record.getScoreFor()),
                value(record.getScoreAgainst()),
                safe(record.getSourceType())
        ));
        return document(
                AiKnowledgeDocumentEntity.TYPE_USER_LEAGUE_RECORD_SUMMARY,
                "v_user_league_records",
                compositeId(record.getLeagueId(), record.getUserId()),
                title,
                content,
                metadata(
                        "leagueId", record.getLeagueId(),
                        "leagueType", record.getLeagueType(),
                        "userId", record.getUserId(),
                        "loginId", record.getLoginId(),
                        "sourceType", record.getSourceType()
                )
        );
    }

    private AiKnowledgeDocumentEntity toMatchResultDocument(UserMatchResultDto result) {
        String title = safe(result.getLoginId()) + " " + safe(result.getLeagueName()) + " 경기 결과";
        String content = limit("""
                경기 결과: userId=%s, 리그=%s, 유형=%s, matchId=%d.
                결과는 %s이고 점수는 %d:%d이다.
                상대는 %s이다.
                기준 데이터는 관리자 승인 완료된 공식 경기 결과만 포함한다.
                데이터 출처는 %s이다.
                """.formatted(
                safe(result.getLoginId()),
                safe(result.getLeagueName()),
                safe(result.getLeagueType()),
                value(result.getMatchId()),
                safe(result.getResult()),
                value(result.getScoreFor()),
                value(result.getScoreAgainst()),
                safe(result.getOpponentLoginId()),
                safe(result.getSourceType())
        ));
        return document(
                AiKnowledgeDocumentEntity.TYPE_MATCH_RESULT_SUMMARY,
                "v_user_match_results",
                compositeId(result.getMatchId(), result.getUserId()),
                title,
                content,
                metadata(
                        "leagueId", result.getLeagueId(),
                        "leagueType", result.getLeagueType(),
                        "matchId", result.getMatchId(),
                        "userId", result.getUserId(),
                        "loginId", result.getLoginId(),
                        "result", result.getResult(),
                        "sourceType", result.getSourceType()
                )
        );
    }

    private AiKnowledgeDocumentEntity toLeagueRecordSummaryDocument(LeagueRecordSummaryDto summary) {
        String title = safe(summary.getLeagueName()) + " 리그 전적 요약";
        String content = limit("""
                리그 전적 요약: 리그=%s, 유형=%s.
                공식 전적 보유 선수는 %d명이고 전체 결과 합계는 %d승 %d패 %d무다.
                점수 합계는 %d득점 %d실점이다.
                기준 데이터는 관리자 승인 완료된 공식 경기 결과만 포함한다.
                """.formatted(
                safe(summary.getLeagueName()),
                safe(summary.getLeagueType()),
                value(summary.getPlayerCount()),
                value(summary.getTotalWins()),
                value(summary.getTotalLosses()),
                value(summary.getTotalDraws()),
                value(summary.getTotalScoreFor()),
                value(summary.getTotalScoreAgainst())
        ));
        return document(
                AiKnowledgeDocumentEntity.TYPE_LEAGUE_RECORD_SUMMARY,
                "v_user_league_records",
                summary.getLeagueId(),
                title,
                content,
                metadata(
                        "leagueId", summary.getLeagueId(),
                        "leagueType", summary.getLeagueType()
                )
        );
    }

    private AiKnowledgeDocumentEntity document(
            String documentType,
            String sourceTable,
            Long sourceId,
            String title,
            String content,
            String metadata
    ) {
        return AiKnowledgeDocumentEntity.builder()
                .documentType(documentType)
                .sourceTable(sourceTable)
                .sourceId(sourceId)
                .title(limit(title, 200))
                .content(content)
                .metadata(metadata)
                .build();
    }

    private String metadata(Object... keyValues) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                values.put(String.valueOf(key), keyValues[i + 1]);
            }
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Long compositeId(Long left, Long right) {
        long safeLeft = left == null ? 0L : left;
        long safeRight = right == null ? 0L : right;
        return safeLeft * 1_000_000_000L + safeRight;
    }

    private String limit(String text) {
        return limit(text, MAX_CONTENT_LENGTH);
    }

    private String limit(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.strip();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "미정" : value;
    }

    private long value(Number value) {
        return value == null ? 0L : value.longValue();
    }
}
