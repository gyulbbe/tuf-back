package io.github.gyulbbe.ai.repository;

import io.github.gyulbbe.ai.entity.AiKnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AiKnowledgeDocumentRepository extends JpaRepository<AiKnowledgeDocumentEntity, Long> {
    List<AiKnowledgeDocumentEntity> findAllByDocumentTypeIn(Collection<String> documentTypes);
}
