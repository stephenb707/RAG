package com.rag.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "repositories")
public class RepositoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "root_path")
    private String rootPath;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public RepositoryEntity() {}

    public RepositoryEntity(String name, String rootPath) {
        this.name = name;
        this.rootPath = rootPath;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getRootPath() { return rootPath; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }
}
