package com.rag.backend.repo;

import com.rag.backend.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepo extends JpaRepository<DocumentEntity, Long> {}
