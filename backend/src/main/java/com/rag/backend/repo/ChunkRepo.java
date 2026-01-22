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

    @Query(value = """
        SELECT c.*
        FROM chunks c
        INNER JOIN documents d ON c.document_id = d.id
        INNER JOIN repositories r ON d.repository_id = r.id
        WHERE r.name = :repoName
        AND c.embedding IS NULL
        ORDER BY c.id
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkEntity> findTopMissingEmbeddingsForRepo(@Param("repoName") String repoName, @Param("limit") int limit);

    @Query(value = """
        SELECT COUNT(*)
        FROM chunks c
        INNER JOIN documents d ON c.document_id = d.id
        INNER JOIN repositories r ON d.repository_id = r.id
        WHERE r.name = :repoName
        AND c.embedding IS NULL
        """, nativeQuery = true)
    long countMissingEmbeddingsForRepo(@Param("repoName") String repoName);

    @Query(value = "SELECT COUNT(*) FROM chunks", nativeQuery = true)
    long countTotalChunks();

    @Query(value = "SELECT COUNT(*) FROM chunks WHERE embedding IS NOT NULL", nativeQuery = true)
    long countChunksWithEmbedding();

}
