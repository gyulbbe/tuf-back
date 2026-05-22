package io.github.gyulbbe.chat.repository;

import io.github.gyulbbe.chat.entity.AiChatSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatSettingsRepository extends JpaRepository<AiChatSettingsEntity, String> {
}
