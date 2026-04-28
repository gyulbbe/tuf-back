package io.github.gyulbbe.site.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum SiteMenuKey {
    CHAT("chat"),
    DRAFT_PROLEAGUE("draft.proleague"),
    DRAFT_CONTENT("draft.content"),
    GAME("game"),
    GALLERY("gallery"),
    ADMIN_DRAFT_HISTORY("admin.draftHistory"),
    ADMIN_USERS("admin.users"),
    EXTERNAL_RECORD_MANAGER("external.recordManager"),
    EXTERNAL_BETTING("external.betting");

    private static final Set<String> RESERVED_KEYS = Set.of("admin", "admin.menuVisibility");

    private final String menuKey;

    public static SiteMenuKey fromMenuKey(String menuKey) {
        if (menuKey == null) {
            return null;
        }
        String normalizedMenuKey = menuKey.trim();
        return Arrays.stream(values())
                .filter(key -> key.menuKey.equals(normalizedMenuKey))
                .findFirst()
                .orElse(null);
    }

    public static boolean isReserved(String menuKey) {
        return menuKey != null && RESERVED_KEYS.contains(menuKey.trim());
    }

    public static List<String> orderedMenuKeys() {
        return Arrays.stream(values())
                .map(SiteMenuKey::getMenuKey)
                .toList();
    }
}
