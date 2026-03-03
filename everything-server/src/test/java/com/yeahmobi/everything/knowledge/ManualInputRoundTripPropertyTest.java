package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for manual input knowledge content round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 34: 手动输入知识内容 round-trip</p>
 *
 * <p><b>Validates: Requirements 15.5</b></p>
 */
class ManualInputRoundTripPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 34: 手动输入知识内容 round-trip
    void manualInputRoundTrip(
            @ForAll("titles") String title,
            @ForAll("contents") String content
    ) {
        // **Validates: Requirements 15.5**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Create from manual input
        KnowledgeFile created = service.createFromManualInput(title, content);

        // Retrieve from repository
        KnowledgeFile retrieved = fileRepo.getFile(created.id()).orElse(null);

        assertNotNull(retrieved, "Created file should be retrievable");
        assertEquals(content, retrieved.extractedText(),
                "extractedText should match the manual input content");
        assertEquals("manual", retrieved.sourceType(),
                "sourceType should be 'manual'");
        assertEquals(title, retrieved.fileName(),
                "fileName should match the title");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> titles() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(50).alpha();
    }

    @Provide
    Arbitrary<String> contents() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(200).alpha();
    }
}
