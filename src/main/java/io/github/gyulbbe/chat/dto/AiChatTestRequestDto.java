package io.github.gyulbbe.chat.dto;

import lombok.Data;

@Data
public class AiChatTestRequestDto {
    private AiChatTestProvider provider;
    private String message;
}
