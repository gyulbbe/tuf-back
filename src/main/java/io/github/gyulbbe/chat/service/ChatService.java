package io.github.gyulbbe.chat.service;

import io.github.gyulbbe.chat.dto.RequestChatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatModel chatModel;

    public String chat(RequestChatDto requestChatDto) {
        try {
            // 메시지 리스트 생성
            List<Message> messages = new ArrayList<>();

            // 프롬프트
            String systemPrompt = buildSystemPrompt();
            messages.add(new SystemMessage(systemPrompt));

            // 사용자 메시지 추가
            messages.add(new UserMessage(requestChatDto.getText()));

            // Prompt 생성 및 AI 호출
            Prompt prompt = new Prompt(messages);
            String response = chatModel.call(prompt).getResult().getOutput().getText();

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

    /**
     * 말투 타입에 따른 시스템 프롬프트 생성
     * 모든 경우에 한국어로만 답변하도록 강제
     */
    private String buildSystemPrompt() {
        String koreanOnly = "You must answer ONLY in Korean language. Never use Chinese or any other language. Don't be formal.";

        return koreanOnly;
    }
}
