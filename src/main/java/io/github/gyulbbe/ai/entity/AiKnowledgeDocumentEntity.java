package io.github.gyulbbe.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@SequenceGenerator(
        name = "ai_knowledge_documents_seq_gen",
        sequenceName = "AI_KNOWLEDGE_DOCUMENTS_SEQ",
        allocationSize = 1
)
@Table(
        name = "AI_KNOWLEDGE_DOCUMENTS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_AI_KNOWLEDGE_DOCUMENT_SOURCE",
                        columnNames = {"DOCUMENT_TYPE", "SOURCE_TABLE", "SOURCE_ID"}
                )
        }
)
public class AiKnowledgeDocumentEntity {

    public static final String TABLE_NAME = "ai_knowledge_documents";
    public static final String TYPE_USER_LEAGUE_RECORD_SUMMARY = "USER_LEAGUE_RECORD_SUMMARY";
    public static final String TYPE_MATCH_RESULT_SUMMARY = "MATCH_RESULT_SUMMARY";
    public static final String TYPE_LEAGUE_RECORD_SUMMARY = "LEAGUE_RECORD_SUMMARY";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_knowledge_documents_seq_gen")
    private Long id;

    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 50)
    private String documentType;

    @Column(name = "SOURCE_TABLE", nullable = false, length = 50)
    private String sourceTable;

    @Column(name = "SOURCE_ID", nullable = false)
    private Long sourceId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "CONTENT", nullable = false, length = 4000)
    private String content;

    @Column(name = "METADATA", length = 4000)
    private String metadata;

    @Column(name = "REG_DATE", nullable = false, updatable = false)
    private LocalDateTime regDate;

    @Column(name = "UPDATE_DATE", nullable = false)
    private LocalDateTime updateDate;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (regDate == null) {
            regDate = now;
        }
        if (updateDate == null) {
            updateDate = regDate;
        }
    }

    @PreUpdate
    void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
