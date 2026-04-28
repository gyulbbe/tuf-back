package io.github.gyulbbe.board.dto;

import org.springframework.util.StringUtils;

import java.util.Locale;

public enum BoardSearchType {
    USER_ID,
    TITLE,
    TEXT;

    public static BoardSearchType from(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim()
                .replace("-", "_")
                .replace(" ", "_")
                .toUpperCase(Locale.ROOT);

        if ("USER".equals(normalized) || "USERID".equals(normalized)) {
            return USER_ID;
        }

        return BoardSearchType.valueOf(normalized);
    }
}
