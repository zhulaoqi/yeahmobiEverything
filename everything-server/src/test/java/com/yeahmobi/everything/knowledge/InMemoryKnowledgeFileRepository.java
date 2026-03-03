package com.yeahmobi.everything.knowledge;

import com.yeahmobi.everything.repository.mysql.KnowledgeFileRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of KnowledgeFileRepository for testing.
 */
class InMemoryKnowledgeFileRepository implements KnowledgeFileRepository {

    private final Map<String, KnowledgeFile> store = new ConcurrentHashMap<>();

    @Override
    public void saveFile(KnowledgeFile file) {
        store.put(file.id(), file);
    }

    @Override
    public Optional<KnowledgeFile> getFile(String fileId) {
        return Optional.ofNullable(store.get(fileId));
    }

    @Override
    public List<KnowledgeFile> getAllFiles() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void updateFile(KnowledgeFile file) {
        store.put(file.id(), file);
    }

    @Override
    public void deleteFile(String fileId) {
        store.remove(fileId);
    }
}
