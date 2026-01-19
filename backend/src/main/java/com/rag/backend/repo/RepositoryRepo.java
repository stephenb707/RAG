package com.rag.backend.repo;

import com.rag.backend.entity.RepositoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryRepo extends JpaRepository<RepositoryEntity, Long> {}
