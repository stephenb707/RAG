package com.rag.backend.indexing;

import com.rag.backend.entity.*;
import com.rag.backend.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Service
public class IndexingService {

    private final RepositoryRepo repositoryRepo;
    private final DocumentRepo documentRepo;
    private final ChunkRepo chunkRepo;

    // Phase 3 tuning knobs
    private final Chunker chunker = new Chunker(120, 20);

    // Ignore folders common in codebases
    private static final Set<String> IGNORE_DIRS = Set.of(
            ".git", ".next", "node_modules", "target", "build", "dist", "out",
            ".idea", ".vscode", ".gradle", ".mvn"
    );

    // Keep this conservative to avoid indexing binaries
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "groovy",
            "js", "ts", "tsx", "jsx",
            "md", "txt",
            "yml", "yaml", "json",
            "xml", "properties",
            "sql", "env"
    );

    public record IndexResult(
            long repositoryId,
            int filesScanned,
            int filesIndexed,
            int filesSkipped,
            int documentsUpserted,
            int chunksCreated,
            long elapsedMs
    ) {}

    public IndexingService(RepositoryRepo repositoryRepo, DocumentRepo documentRepo, ChunkRepo chunkRepo) {
        this.repositoryRepo = repositoryRepo;
        this.documentRepo = documentRepo;
        this.chunkRepo = chunkRepo;
    }

    @Transactional
    public IndexResult indexFolder(String repoName, String rootPath) throws IOException {
        long startTime = System.currentTimeMillis();
        
        Path root = Paths.get(rootPath).normalize().toAbsolutePath();
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Path does not exist or is not a directory: " + root);
        }

        RepositoryEntity repo = repositoryRepo.save(new RepositoryEntity(repoName, root.toString()));

        int filesScanned = 0;
        int filesIndexed = 0;
        int filesSkipped = 0;
        int documentsUpserted = 0;
        int chunks = 0;

        try (Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> it = paths.iterator();
            while (it.hasNext()) {
                Path p = it.next();

                if (Files.isDirectory(p)) {
                    if (shouldIgnoreDir(p)) {
                        // Skip subtree
                        // Files.walk doesn't support skipping easily without FileVisitor,
                        // but this simple filter is usually fine. We'll still avoid reading files under ignored dirs below.
                    }
                    continue;
                }

                if (!Files.isRegularFile(p)) continue;
                
                filesScanned++;
                
                if (isUnderIgnoredDir(root, p)) {
                    filesSkipped++;
                    continue;
                }
                if (!isAllowedFile(p)) {
                    filesSkipped++;
                    continue;
                }

                List<String> lines;
                try {
                    lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (MalformedInputException mie) {
                    // Non-UTF8 or binary-ish file, skip.
                    filesSkipped++;
                    continue;
                } catch (Exception ex) {
                    // Skip unreadable files
                    filesSkipped++;
                    continue;
                }

                String fullText = String.join("\n", lines);
                String docHash = Hashing.sha256(fullText);

                String relativePath = root.relativize(p.toAbsolutePath()).toString().replace("\\", "/");
                DocumentEntity doc = documentRepo.save(new DocumentEntity(repo, relativePath, docHash));
                filesIndexed++;
                documentsUpserted++;

                List<Chunker.Chunk> chunkList = chunker.chunkLines(lines);
                List<ChunkEntity> chunkEntities = new ArrayList<>(chunkList.size());

                for (Chunker.Chunk c : chunkList) {
                    String chunkHash = Hashing.sha256(c.content());
                    chunkEntities.add(new ChunkEntity(
                            doc,
                            c.chunkIndex(),
                            c.startLine(),
                            c.endLine(),
                            c.content(),
                            chunkHash
                    ));
                }

                chunkRepo.saveAll(chunkEntities);
                chunks += chunkEntities.size();
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        return new IndexResult(repo.getId(), filesScanned, filesIndexed, filesSkipped, documentsUpserted, chunks, elapsedMs);
    }

    private boolean shouldIgnoreDir(Path dir) {
        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
        return IGNORE_DIRS.contains(name);
    }

    private boolean isUnderIgnoredDir(Path root, Path file) {
        Path rel = root.relativize(file.toAbsolutePath());
        for (Path part : rel) {
            if (IGNORE_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private boolean isAllowedFile(Path file) {
        String fn = file.getFileName().toString();
        int idx = fn.lastIndexOf('.');
        if (idx < 0) return false;
        String ext = fn.substring(idx + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext);
    }
}
