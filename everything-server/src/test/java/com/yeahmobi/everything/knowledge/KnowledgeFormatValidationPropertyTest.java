package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for knowledge file format validation.
 *
 * <p>Feature: yeahmobi-everything, Property 21: 知识库文件格式验证</p>
 *
 * <p><b>Validates: Requirements 13.2, 14.5</b></p>
 */
class KnowledgeFormatValidationPropertyTest {

    private final KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
            new InMemoryKnowledgeFileRepository(),
            new InMemoryBindingRepository(),
            new NoOpCacheService());

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 21: 知识库文件格式验证
    void supportedFormatsAccepted(@ForAll("supportedFileNames") String fileName) {
        // **Validates: Requirements 13.2, 14.5**
        assertTrue(service.isSupportedFormat(fileName),
                "File '" + fileName + "' should be a supported format");
    }

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 21: 知识库文件格式验证
    void unsupportedFormatsRejected(@ForAll("unsupportedFileNames") String fileName) {
        // **Validates: Requirements 13.2, 14.5**
        assertFalse(service.isSupportedFormat(fileName),
                "File '" + fileName + "' should NOT be a supported format");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> supportedFileNames() {
        Arbitrary<String> baseName = Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
        Arbitrary<String> ext = Arbitraries.of("pdf", "md", "txt");
        return Combinators.combine(baseName, ext).as((name, e) -> name + "." + e);
    }

    @Provide
    Arbitrary<String> unsupportedFileNames() {
        Arbitrary<String> baseName = Arbitraries.strings().ofMinLength(1).ofMaxLength(20).alpha();
        Arbitrary<String> ext = Arbitraries.of("doc", "docx", "xlsx", "jpg", "png", "html", "csv", "zip", "exe");
        return Combinators.combine(baseName, ext).as((name, e) -> name + "." + e);
    }
}
