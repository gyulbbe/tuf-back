package io.github.gyulbbe.chat.service;

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

    public String chat(RequestChatDto requestChatDto) {
        try {
            String systemPrompt = buildSystemPrompt();
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

    private String buildSystemPrompt() {
        return "You must answer ONLY in Korean language. Never use Chinese or any other language. Don't be formal.";
    }
}