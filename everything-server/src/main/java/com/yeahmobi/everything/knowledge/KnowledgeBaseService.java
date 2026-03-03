package com.yeahmobi.everything.knowledge;

import java.io.File;
import java.util.List;

/**
 * Service interface for knowledge base management.
 * Handles file upload, text extraction, binding/unbinding, and merged text retrieval.
 */
public interface KnowledgeBaseService {

    KnowledgeFile uploadFile(File file) throws FileProcessingException;

    List<KnowledgeFile> uploadFiles(List<File> files) throws FileProcessingException;

    KnowledgeFile createFromManualInput(String title, String content);

    KnowledgeFile updateFile(String fileId, File newFile) throws FileProcessingException;

    KnowledgeFile updateManualContent(String fileId, String content);

    void deleteFile(String fileId);

    List<KnowledgeFile> getAllFiles();

    void bindFileToSkill(String skillId, String fileId);

    void unbindFileFromSkill(String skillId, String fileId);

    List<KnowledgeFile> getFilesForSkill(String skillId);

    String getMergedKnowledgeText(String skillId);

    boolean isSupportedFormat(String fileName);

    String extractText(File file) throws FileProcessingException;
}
