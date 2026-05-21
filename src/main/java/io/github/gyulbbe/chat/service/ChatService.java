package io.github.gyulbbe.chat.service;

import io.github.gyulbbe.ai.service.AiRecordChatContextService;
import io.github.gyulbbe.chat.dto.RequestChatDto;
import io.github.gyulbbe.chat.provider.ChatProviderRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatProviderRouter chatProviderRouter;
    private final AiRecordChatContextService aiRecordChatContextService;

    public String chat(RequestChatDto requestChatDto) {
        try {
            String systemPrompt = buildSystemPrompt(aiRecordChatContextService.buildContext(requestChatDto));
            String response = chatProviderRouter.chat(systemPrompt, requestChatDto.getText());

            log.info("User: {}, Question: {}",
                    requestChatDto.getUserId(),
                    requestChatDto.getText());
            log.info("AI Response: {}", response);

            return response;

        } catch (Exception e) {
            log.error("AI 채팅 중 오류 발생", e);
            return "죄송합니다. 응답을 생성하는 중 오류가 발생했습니다.";
        }
    }

    private String buildSystemPrompt(String recordContext) {
        String basePrompt = """
                You must answer ONLY in Korean language. Never use Chinese or any other language. Don't be formal.
                전적, 승패, 점수, 우승 기록에 대한 질문은 공식 전적 SQL 결과를 가장 우선해서 답한다.
                전적 검색 문서는 보조 맥락으로만 사용하고, 공식 전적 SQL 결과와 충돌하면 공식 전적 SQL 결과를 따른다.
                공식 전적 SQL 결과가 없으면 수치를 추측하지 말고 공식 기록이 없다고 말한다.
                """;
        if (recordContext == null || recordContext.isBlank()) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + recordContext;
    }
}
