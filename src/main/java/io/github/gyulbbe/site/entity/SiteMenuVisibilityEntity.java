package io.github.gyulbbe.site.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "SITE_MENU_VISIBILITY")
public class SiteMenuVisibilityEntity {

    @Id
    @Column(name = "MENU_KEY", nullable = false, length = 100)
    private String menuKey;

    @Column(name = "VISIBLE", nullable = false)
    private Integer visible;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private SiteMenuVisibilityEntity(String menuKey, Integer visible, Long updatedBy, LocalDateTime updatedAt) {
        this.menuKey = menuKey;
        this.visible = visible;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public static SiteMenuVisibilityEntity create(String menuKey, Boolean visible, Long updatedBy) {
        return SiteMenuVisibilityEntity.builder()
                .menuKey(menuKey)
                .visible(toFlag(visible))
                .updatedBy(updatedBy)
                .build();
    }

    public void update(Boolean visible, Long updatedBy) {
        this.visible = toFlag(visible);
        this.updatedBy = updatedBy;
    }

    public boolean isVisible() {
        return Integer.valueOf(1).equals(visible);
    }

    @PrePersist
    void onCreate() {
        if (visible == null) {
            visible = 1;
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private static Integer toFlag(Boolean visible) {
        return Boolean.TRUE.equals(visible) ? 1 : 0;
    }
}
