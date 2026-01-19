package com.rag.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "documents",
    uniqueConstraints = @UniqueConstraint(name = "uq_documents_repo_file", columnNames = {"repository_id", "file_path"})
)
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public DocumentEntity() {}

    public DocumentEntity(RepositoryEntity repository, String filePath, String contentHash) {
        this.repository = repository;
        this.filePath = filePath;
        this.contentHash = contentHash;
    }

    public Long getId() { return id; }
    public RepositoryEntity getRepository() { return repository; }
    public String getFilePath() { return filePath; }
    public String getContentHash() { return contentHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setRepository(RepositoryEntity repository) { this.repository = repository; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
}
