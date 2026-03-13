package com.yeahmobi.everything.knowledge;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.KnowledgeFileRepository;
import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of {@link KnowledgeBaseService}.
 * <p>
 * Manages knowledge base files: upload, text extraction, binding to Skills,
 * and merged text retrieval with Redis caching.
 * </p>
 */
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "md", "txt");
    static final long KNOWLEDGE_CACHE_TTL_SECONDS = 1800; // 30 minutes

    private final KnowledgeFileRepository fileRepository;
    private final SkillKnowledgeBindingRepository bindingRepository;
    private final CacheService cacheService;

    public KnowledgeBaseServiceImpl(KnowledgeFileRepository fileRepository,
                                    SkillKnowledgeBindingRepository bindingRepository,
                                    CacheService cacheService) {
        this.fileRepository = fileRepository;
        this.bindingRepository = bindingRepository;
        this.cacheService = cacheService;
    }

    @Override
    public KnowledgeFile uploadFile(File file) throws FileProcessingException {
        if (!isSupportedFormat(file.getName())) {
            throw new FileProcessingException("Unsupported file format: " + file.getName());
        }
        String text = extractText(file);
        String ext = getExtension(file.getName());
        long now = System.currentTimeMillis();
        KnowledgeFile kf = new KnowledgeFile(
                UUID.randomUUID().toString(), file.getName(), ext,
                file.length(), "upload", text, now, now
        );
        fileRepository.saveFile(kf);
        return kf;
    }

    @Override
    public List<KnowledgeFile> uploadFiles(List<File> files) throws FileProcessingException {
        List<KnowledgeFile> results = new ArrayList<>();
        for (File file : files) {
            results.add(uploadFile(file));
        }
        return results;
    }

    @Override
    public KnowledgeFile createFromManualInput(String title, String content) {
        long now = System.currentTimeMillis();
        KnowledgeFile kf = new KnowledgeFile(
                UUID.randomUUID().toString(), title, "manual",
                0L, "manual", content, now, now
        );
        fileRepository.saveFile(kf);
        return kf;
    }

    @Override
    public KnowledgeFile updateFile(String fileId, File newFile) throws FileProcessingException {
        if (!isSupportedFormat(newFile.getName())) {
            throw new FileProcessingException("Unsupported file format: " + newFile.getName());
        }
        Optional<KnowledgeFile> existing = fileRepository.getFile(fileId);
        if (existing.isEmpty()) {
            throw new FileProcessingException("Knowledge file not found: " + fileId);
        }
        String text = extractText(newFile);
        String ext = getExtension(newFile.getName());
        long now = System.currentTimeMillis();
        KnowledgeFile updated = new KnowledgeFile(
                fileId, newFile.getName(), ext,
                newFile.length(), "upload", text,
                existing.get().uploadedAt(), now
        );
        fileRepository.updateFile(updated);
        invalidateRelatedCaches(fileId);
        return updated;
    }

    @Override
    public KnowledgeFile updateManualContent(String fileId, String content) {
        Optional<KnowledgeFile> existing = fileRepository.getFile(fileId);
        if (existing.isEmpty()) {
            throw new RuntimeException("Knowledge file not found: " + fileId);
        }
        KnowledgeFile old = existing.get();
        long now = System.currentTimeMillis();
        KnowledgeFile updated = new KnowledgeFile(
                fileId, old.fileName(), old.fileType(),
                0L, "manual", content,
                old.uploadedAt(), now
        );
        fileRepository.updateFile(updated);
        invalidateRelatedCaches(fileId);
        return updated;
    }

    @Override
    public void deleteFile(String fileId) {
        // Unbind from all skills first (cascade handled by FK in MySQL,
        // but we also invalidate caches for affected skills)
        List<String> affectedSkillIds = bindingRepository.getSkillIdsForFile(fileId);
        bindingRepository.unbindAllForFile(fileId);
        fileRepository.deleteFile(fileId);
        for (String skillId : affectedSkillIds) {
            try {
                cacheService.invalidateKnowledgeCache(skillId);
            } catch (Exception e) {
                log.warn("Failed to invalidate cache for skill: {}", skillId, e);
            }
        }
    }

    @Override
    public List<KnowledgeFile> getAllFiles() {
        return fileRepository.getAllFiles();
    }

    @Override
    public void bindFileToSkill(String skillId, String fileId) {
        bindingRepository.bind(skillId, fileId);
        try {
            cacheService.invalidateKnowledgeCache(skillId);
        } catch (Exception e) {
            log.warn("Failed to invalidate knowledge cache for skill: {}", skillId, e);
        }
    }

    @Override
    public void unbindFileFromSkill(String skillId, String fileId) {
        bindingRepository.unbind(skillId, fileId);
        try {
            cacheService.invalidateKnowledgeCache(skillId);
        } catch (Exception e) {
            log.warn("Failed to invalidate knowledge cache for skill: {}", skillId, e);
        }
    }

    @Override
    public List<KnowledgeFile> getFilesForSkill(String skillId) {
        List<String> fileIds = bindingRepository.getFileIdsForSkill(skillId);
        List<KnowledgeFile> files = new ArrayList<>();
        for (String fileId : fileIds) {
            fileRepository.getFile(fileId).ifPresent(files::add);
        }
        return files;
    }

    @Override
    public String getMergedKnowledgeText(String skillId) {
        // Try cache first
        try {
            Optional<String> cached = cacheService.getCachedKnowledgeText(skillId);
            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (Exception e) {
            log.warn("Redis unavailable, falling back to DB", e);
        }

        // Fetch from DB
        List<KnowledgeFile> files = getFilesForSkill(skillId);
        String merged = files.stream()
                .map(KnowledgeFile::extractedText)
                .filter(t -> t != null && !t.isEmpty())
                .collect(Collectors.joining("\n\n"));

        // Cache the result
        if (!merged.isEmpty()) {
            try {
                cacheService.cacheKnowledgeText(skillId, merged, KNOWLEDGE_CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("Failed to cache knowledge text", e);
            }
        }
        return merged;
    }

    @Override
    public boolean isSupportedFormat(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        String ext = getExtension(fileName);
        return SUPPORTED_EXTENSIONS.contains(ext);
    }

    @Override
    public String extractText(File file) throws FileProcessingException {
        try {
            String ext = getExtension(file.getName());
            return switch (ext) {
                case "txt", "md" -> Files.readString(file.toPath());
                case "pdf" -> extractPdfText(file);
                default -> throw new FileProcessingException("Unsupported format: " + ext);
            };
        } catch (IOException e) {
            throw new FileProcessingException("Failed to extract text from: " + file.getName(), e);
        }
    }

    // ---- Internal helpers ----

    private String extractPdfText(File file) throws FileProcessingException {
        // Simplified PDF text extraction: read raw bytes and attempt basic text extraction.
        // In production, a library like Apache PDFBox would be used.
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to extract PDF text: " + file.getName(), e);
        }
    }

    static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private void invalidateRelatedCaches(String fileId) {
        try {
            List<String> skillIds = bindingRepository.getSkillIdsForFile(fileId);
            for (String skillId : skillIds) {
                cacheService.invalidateKnowledgeCache(skillId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate related caches for file: {}", fileId, e);
        }
    }
}
