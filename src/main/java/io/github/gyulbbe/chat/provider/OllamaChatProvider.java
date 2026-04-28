package io.github.gyulbbe.chat.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OllamaChatProvider {

    private final ChatModel chatModel;

    public String chat(String systemPrompt, String userMessage) {
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        );
        return chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
    }
}