package io.github.gyulbbe.chat.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiChatTestResponseDto {
    private AiChatTestProvider provider;
    private String model;
    private String response;
}
