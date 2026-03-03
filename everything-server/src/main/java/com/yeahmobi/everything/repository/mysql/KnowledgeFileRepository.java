package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.knowledge.KnowledgeFile;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for knowledge base file persistence in MySQL.
 */
public interface KnowledgeFileRepository {

    void saveFile(KnowledgeFile file);

    Optional<KnowledgeFile> getFile(String fileId);

    List<KnowledgeFile> getAllFiles();

    void updateFile(KnowledgeFile file);

    void deleteFile(String fileId);
}
