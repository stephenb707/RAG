package com.rag.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "chunks",
    uniqueConstraints = @UniqueConstraint(name = "uq_chunks_doc_index", columnNames = {"document_id", "chunk_index"})
)
public class ChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "start_line")
    private Integer startLine;

    @Column(name = "end_line")
    private Integer endLine;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public ChunkEntity() {}

    public ChunkEntity(DocumentEntity document, Integer chunkIndex, Integer startLine, Integer endLine, String content, String contentHash) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.startLine = startLine;
        this.endLine = endLine;
        this.content = content;
        this.contentHash = contentHash;
    }

    public Long getId() { return id; }
    public DocumentEntity getDocument() { return document; }
    public Integer getChunkIndex() { return chunkIndex; }
    public Integer getStartLine() { return startLine; }
    public Integer getEndLine() { return endLine; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setDocument(DocumentEntity document) { this.document = document; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }
    public void setContent(String content) { this.content = content; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
}
