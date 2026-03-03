package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for text extraction round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 23: 文本提取 round-trip</p>
 *
 * <p><b>Validates: Requirements 13.6, 14.2</b></p>
 *
 * <p>For any valid TXT or Markdown file content, uploading the file should
 * produce a KnowledgeFile whose extractedText contains the original content.</p>
 */
class TextExtractionRoundTripPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 23: 文本提取 round-trip
    void txtFileExtractionRoundTrip(@ForAll("textContents") String content) throws Exception {
        // **Validates: Requirements 13.6, 14.2**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Create a temp .txt file
        Path tempFile = Files.createTempFile("test-knowledge-", ".txt");
        try {
            Files.writeString(tempFile, content);
            File file = tempFile.toFile();

            KnowledgeFile result = service.uploadFile(file);

            assertNotNull(result);
            assertEquals(content, result.extractedText(),
                    "Extracted text should match original content for .txt file");
            assertEquals("txt", result.fileType());
            assertEquals("upload", result.sourceType());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 23: 文本提取 round-trip
    void mdFileExtractionRoundTrip(@ForAll("textContents") String content) throws Exception {
        // **Validates: Requirements 13.6, 14.2**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Create a temp .md file
        Path tempFile = Files.createTempFile("test-knowledge-", ".md");
        try {
            Files.writeString(tempFile, content);
            File file = tempFile.toFile();

            KnowledgeFile result = service.uploadFile(file);

            assertNotNull(result);
            assertEquals(content, result.extractedText(),
                    "Extracted text should match original content for .md file");
            assertEquals("md", result.fileType());
            assertEquals("upload", result.sourceType());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> textContents() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(200)
                .filter(s -> !s.isEmpty());
    }
}
