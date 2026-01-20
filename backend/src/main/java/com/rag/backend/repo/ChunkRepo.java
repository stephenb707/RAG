package com.rag.backend.repo;

import com.rag.backend.entity.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChunkRepo extends JpaRepository<ChunkEntity, Long> {

    @Modifying
    @Query(value = "UPDATE chunks SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    int updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);

    @Query(value = """
        SELECT *
        FROM chunks
        WHERE embedding IS NOT NULL
        ORDER BY embedding <-> CAST(:queryEmbedding AS vector), id
        LIMIT :k
        """, nativeQuery = true)
    List<ChunkEntity> searchTopK(@Param("queryEmbedding") String queryEmbedding, @Param("k") int k);

    @Query(value = """
        SELECT *
        FROM chunks
        WHERE embedding IS NULL
        ORDER BY id
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkEntity> findTopMissingEmbeddings(@Param("limit") int limit);

}
