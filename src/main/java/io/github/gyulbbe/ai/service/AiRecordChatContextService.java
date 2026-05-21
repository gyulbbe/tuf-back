package io.github.gyulbbe.ai.service;

import io.github.gyulbbe.ai.dto.UserLeagueRecordDto;
import io.github.gyulbbe.ai.entity.AiKnowledgeDocumentEntity;
import io.github.gyulbbe.ai.mapper.AiRecordMapper;
import io.github.gyulbbe.chat.dto.RequestChatDto;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingService;
import io.github.gyulbbe.common.utils.embeddingVector.EmbeddingVectorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecordChatContextService {

    private static final int MAX_RECORD_LINES = 12;
    private static final int MAX_VECTOR_DOCUMENTS = 5;
    private static final Pattern LOGIN_ID_TOKEN = Pattern.compile("[A-Za-z0-9_.-]{2,40}");
    private static final List<String> RECORD_KEYWORDS = List.of(
            "전적", "승패", "승률", "몇승", "몇 승", "몇패", "몇 패", "승", "패",
            "우승", "준우승", "활약", "요약", "리그", "경기", "점수",
            "record", "win", "loss", "league", "match", "score"
    );

    private final AiRecordMapper aiRecordMapper;
    private final EmbeddingService embeddingService;

    public String buildContext(RequestChatDto request) {
        String question = request == null ? null : request.getText();
        if (!isRecordRelated(question)) {
            return "";
        }

        List<String> sections = new ArrayList<>();
        String officialRecordSection = buildOfficialRecordSection(request);
        if (!officialRecordSection.isBlank()) {
            sections.add(officialRecordSection);
        }

        String vectorSection = buildVectorSection(question);
        if (!vectorSection.isBlank()) {
            sections.add(vectorSection);
        }

        if (sections.isEmpty()) {
            return """
                    공식 전적 컨텍스트:
                    현재 질문과 연결할 수 있는 공식 전적 또는 검색 문서를 찾지 못했다.
                    전적 수치를 묻는 질문이면 추측하지 말고 공식 기록이 없다고 답한다.
                    """;
        }
        return String.join("\n\n", sections);
    }

    private String buildOfficialRecordSection(RequestChatDto request) {
        Set<String> loginIds = extractLoginIds(request);
        if (loginIds.isEmpty()) {
            return "";
        }

        try {
            List<UserLeagueRecordDto> records = aiRecordMapper.findUserLeagueRecordsByLoginIds(new ArrayList<>(loginIds));
            if (records.isEmpty()) {
                return """
                        공식 전적 SQL 결과:
                        질문에서 찾은 userId 후보(%s)에 대한 공식 전적이 없다.
                        """.formatted(String.join(", ", loginIds));
            }

            StringBuilder builder = new StringBuilder("공식 전적 SQL 결과:\n");
            records.stream()
                    .limit(MAX_RECORD_LINES)
                    .forEach(record -> builder.append("- ")
                            .append(record.getLoginId())
                            .append(" / ")
                            .append(record.getLeagueName())
                            .append(" / ")
                            .append(record.getLeagueType())
                            .append(": ")
                            .append(value(record.getWins()))
                            .append("승 ")
                            .append(value(record.getLosses()))
                            .append("패 ")
                            .append(value(record.getDraws()))
                            .append("무, 점수 ")
                            .append(value(record.getScoreFor()))
                            .append(":")
                            .append(value(record.getScoreAgainst()))
                            .append(", 출처 ")
                            .append(record.getSourceType())
                            .append("\n"));
            if (records.size() > MAX_RECORD_LINES) {
                builder.append("- 외 ").append(records.size() - MAX_RECORD_LINES).append("건 생략\n");
            }
            return builder.toString().strip();
        } catch (Exception e) {
            log.warn("Failed to load official record context.", e);
            return "";
        }
    }

    private String buildVectorSection(String question) {
        try {
            List<EmbeddingVectorDto> documents = embeddingService.findSimilarVectors(
                    question,
                    AiKnowledgeDocumentEntity.TABLE_NAME,
                    MAX_VECTOR_DOCUMENTS
            );
            if (documents.isEmpty()) {
                return "";
            }
            StringBuilder builder = new StringBuilder("전적 검색 문서:\n");
            for (EmbeddingVectorDto document : documents) {
                if (document.getText() == null || document.getText().isBlank()) {
                    continue;
                }
                builder.append("- ")
                        .append(document.getText().replaceAll("\\s+", " ").strip())
                        .append(" (distance=")
                        .append(document.getDistance())
                        .append(")\n");
            }
            return builder.toString().strip();
        } catch (Exception e) {
            log.warn("Failed to load vector record context.", e);
            return "";
        }
    }

    private Set<String> extractLoginIds(RequestChatDto request) {
        Set<String> loginIds = new LinkedHashSet<>();
        if (request != null && request.getUserId() != null && !request.getUserId().isBlank()) {
            loginIds.add(request.getUserId().trim());
        }
        String question = request == null ? "" : request.getText();
        if (question == null || question.isBlank()) {
            return loginIds;
        }
        Matcher matcher = LOGIN_ID_TOKEN.matcher(question);
        while (matcher.find()) {
            String token = matcher.group();
            if (!isGenericToken(token)) {
                loginIds.add(token);
            }
        }
        return loginIds;
    }

    private boolean isRecordRelated(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT).replace(" ", "");
        return RECORD_KEYWORDS.stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT).replace(" ", ""))
                .anyMatch(normalized::contains);
    }

    private boolean isGenericToken(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return Set.of("win", "loss", "record", "league", "match", "score", "personal", "proleague")
                .contains(normalized)
                || normalized.matches("\\d+");
    }

    private int value(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
