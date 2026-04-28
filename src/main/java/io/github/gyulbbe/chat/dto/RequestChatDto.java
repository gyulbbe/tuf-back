package io.github.gyulbbe.chat.dto;

import lombok.Data;

@Data
public class RequestChatDto {
    private String userId;
    private String text;
}
