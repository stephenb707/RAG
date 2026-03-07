package com.rag.backend.agent.fs;

import com.rag.backend.config.RagRepoConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Service
public class RepoFsService {
    private final RagRepoConfig ragRepoConfig;

    private static final Set<String> IGNORE_DIRS = Set.of(
            ".git", ".next", "node_modules", "target", "build", "dist", "out",
            ".idea", ".vscode", ".gradle", ".mvn"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "groovy",
            "js", "ts", "tsx", "jsx",
            "md", "txt",
            "yml", "yaml", "json",
            "xml", "properties",
            "sql", "env",
            "sh", "ps1"
    );

    public RepoFsService(RagRepoConfig ragRepoConfig) {
        this.ragRepoConfig = ragRepoConfig;
    }

    public Path resolveRepoRoot(String repoName) {
        if (repoName == null || repoName.isBlank()) {
            throw new IllegalArgumentException("repoName is required");
        }
        if (!ragRepoConfig.hasRepoRoots()) {
            throw new IllegalArgumentException("RAG_REPO_ROOTS is not configured");
        }
        return ragRepoConfig.resolveRepoRootOrThrow(repoName);
    }

    public Path resolveSafePath(Path repoRoot, String relativePath) {
        String rel = (relativePath == null || relativePath.isBlank()) ? "" : relativePath;

        if (rel.contains("..") || rel.contains("~") || rel.contains("\\")) {
            throw new IllegalArgumentException("Invalid path");
        }

        Path resolved = repoRoot.resolve(rel).normalize().toAbsolutePath();
        if (!resolved.startsWith(repoRoot)) {
            throw new IllegalArgumentException("Path escapes repo root");
        }
        return resolved;
    }

    public boolean isUnderIgnoredDir(Path repoRoot, Path p) {
        Path rel = repoRoot.relativize(p.toAbsolutePath());
        for (Path part : rel) {
            if (IGNORE_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    public boolean isAllowedFile(Path p) {
        if (!Files.isRegularFile(p)) return false;
        String fn = p.getFileName().toString();
        int idx = fn.lastIndexOf('.');
        if (idx < 0) return false;
        String ext = fn.substring(idx + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext);
    }

    public List<String> readLines(Path repoRoot, String relativePath) throws IOException {
        Path p = resolveSafePath(repoRoot, relativePath);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }
        if (isUnderIgnoredDir(repoRoot, p) || !isAllowedFile(p)) {
            throw new IllegalArgumentException("File is not allowed: " + relativePath);
        }
        return Files.readAllLines(p, StandardCharsets.UTF_8);
    }

    public List<Path> listFiles(Path repoRoot, String glob, int maxResults) throws IOException {
        if (maxResults <= 0) maxResults = 50;
        if (maxResults > 500) maxResults = 500;

        String pattern = (glob == null || glob.isBlank()) ? "**/*" : glob;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<Path> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (Files.isDirectory(p)) continue;
                if (isUnderIgnoredDir(repoRoot, p)) continue;
                if (!isAllowedFile(p)) continue;

                Path rel = repoRoot.relativize(p.toAbsolutePath());
                if (matcher.matches(rel)) {
                    results.add(p);
                    if (results.size() >= maxResults) break;
                }
            }
        }
        results.sort(Comparator.comparing(Path::toString));
        return results;
    }

    public List<SearchHit> search(Path repoRoot, String query, int maxResults) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        if (maxResults <= 0) maxResults = 50;
        if (maxResults > 500) maxResults = 500;

        List<SearchHit> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (Files.isDirectory(p)) continue;
                if (isUnderIgnoredDir(repoRoot, p)) continue;
                if (!isAllowedFile(p)) continue;

                List<String> lines;
                try {
                    lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (Exception ex) {
                    continue;
                }

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.contains(query)) {
                        String relPath = repoRoot.relativize(p.toAbsolutePath()).toString().replace("\\", "/");
                        hits.add(new SearchHit(relPath, i + 1, line));
                        if (hits.size() >= maxResults) return hits;
                    }
                }
            }
        }
        return hits;
    }

    public TreeResult tree(Path repoRoot, String relativePath, int depth, int maxEntries) throws IOException {
        if (depth < 0) depth = 0;
        if (depth > 10) depth = 10;
        if (maxEntries <= 0) maxEntries = 500;
        if (maxEntries > 5000) maxEntries = 5000;

        Path start = resolveSafePath(repoRoot, relativePath);
        if (!Files.exists(start)) {
            throw new IllegalArgumentException("Path not found: " + (relativePath == null ? "" : relativePath));
        }
        if (!Files.isDirectory(start)) {
            throw new IllegalArgumentException("Path is not a directory: " + (relativePath == null ? "" : relativePath));
        }

        List<TreeEntry> entries = new ArrayList<>();
        walkTree(repoRoot, start, depth, entries, maxEntries);

        String baseRel = repoRoot.relativize(start.toAbsolutePath()).toString().replace("\\", "/");
        return new TreeResult(baseRel.isBlank() ? "." : baseRel, entries);
    }

    public ReadFileData readFile(Path repoRoot, String relativePath) throws IOException {
        Path p = resolveSafePath(repoRoot, relativePath);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }
        if (isUnderIgnoredDir(repoRoot, p) || !isAllowedFile(p)) {
            throw new IllegalArgumentException("File is not allowed: " + relativePath);
        }
    
        long size = Files.size(p);
        if (size > 2_000_000) throw new IllegalArgumentException("File too large to read: " + relativePath);

        byte[] bytes = Files.readAllBytes(p);
        String sha256 = sha256Hex(bytes);
    
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
    
        return new ReadFileData(lines, sha256);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void walkTree(Path repoRoot, Path dir, int depth, List<TreeEntry> out, int maxEntries) throws IOException {
        if (out.size() >= maxEntries) return;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            List<Path> children = new ArrayList<>();
            for (Path child : ds) {
                children.add(child);
            }
            children.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

            for (Path child : children) {
                if (out.size() >= maxEntries) return;

                String name = child.getFileName() != null ? child.getFileName().toString() : "";
                if (Files.isDirectory(child) && IGNORE_DIRS.contains(name)) {
                    continue;
                }

                boolean isDir = Files.isDirectory(child);
                boolean allowed = isDir || (!isUnderIgnoredDir(repoRoot, child) && isAllowedFile(child));

                if (!isDir && !allowed) continue;

                String relPath = repoRoot.relativize(child.toAbsolutePath()).toString().replace("\\", "/");
                long size = 0L;
                Instant modifiedAt = null;
                try {
                    if (!isDir) size = Files.size(child);
                    modifiedAt = Files.getLastModifiedTime(child).toInstant();
                } catch (Exception ignored) {}

                out.add(new TreeEntry(relPath, isDir ? "dir" : "file", size, modifiedAt));

                if (isDir && depth > 0) {
                    walkTree(repoRoot, child, depth - 1, out, maxEntries);
                }
            }
        }
    }

    public record ReadFileData(List<String> lines, String sha256) {}
    public record SearchHit(String path, int line, String text) {}
    public record TreeEntry(String path, String type, long sizeBytes, Instant modifiedAt) {}
    public record TreeResult(String basePath, List<TreeEntry> entries) {}
}
