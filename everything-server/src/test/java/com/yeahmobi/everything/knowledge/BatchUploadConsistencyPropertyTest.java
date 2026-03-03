package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for batch upload file count consistency.
 *
 * <p>Feature: yeahmobi-everything, Property 33: 批量上传文件数量一致性</p>
 *
 * <p><b>Validates: Requirements 15.4</b></p>
 */
class BatchUploadConsistencyPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 33: 批量上传文件数量一致性
    void batchUploadCountMatchesInput(@ForAll("fileCounts") int fileCount) throws Exception {
        // **Validates: Requirements 15.4**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Create temp files
        List<Path> tempPaths = new ArrayList<>();
        List<File> files = new ArrayList<>();
        try {
            String[] extensions = {".txt", ".md"};
            for (int i = 0; i < fileCount; i++) {
                String ext = extensions[i % extensions.length];
                Path p = Files.createTempFile("batch-test-" + i + "-", ext);
                Files.writeString(p, "Content for file " + i);
                tempPaths.add(p);
                files.add(p.toFile());
            }

            List<KnowledgeFile> results = service.uploadFiles(files);

            assertEquals(fileCount, results.size(),
                    "Number of uploaded files should match input count");
        } finally {
            for (Path p : tempPaths) {
                Files.deleteIfExists(p);
            }
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<Integer> fileCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
