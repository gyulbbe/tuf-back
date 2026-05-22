package io.github.gyulbbe.chat.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OllamaChatProvider {

    private final ChatModel chatModel;

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, null);
    }

    public String chat(String systemPrompt, String userMessage, String model) {
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userMessage)
        );
        Prompt prompt = model == null || model.isBlank()
                ? new Prompt(messages)
                : new Prompt(messages, OllamaOptions.builder().model(model.trim()).build());
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
