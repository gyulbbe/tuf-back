package io.github.gyulbbe.speech.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SpeechDto {
    private String nickname;
    private String chat;
    private LocalDateTime chatDatetime;
}
