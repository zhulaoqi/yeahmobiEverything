package com.yeahmobi.everything.knowledge;

import net.jqwik.api.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for knowledge file binding round-trip.
 *
 * <p>Feature: yeahmobi-everything, Property 19: 知识库文件绑定 round-trip</p>
 *
 * <p><b>Validates: Requirements 13.1, 13.5</b></p>
 */
class KnowledgeBindingRoundTripPropertyTest {

    @Property(tries = 100)
    // Feature: yeahmobi-everything, Property 19: 知识库文件绑定 round-trip
    void bindingRoundTrip(
            @ForAll("skillIds") String skillId,
            @ForAll("knowledgeFileLists") List<KnowledgeFile> files
    ) {
        // **Validates: Requirements 13.1, 13.5**

        InMemoryKnowledgeFileRepository fileRepo = new InMemoryKnowledgeFileRepository();
        InMemoryBindingRepository bindingRepo = new InMemoryBindingRepository();
        NoOpCacheService cacheService = new NoOpCacheService();
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(fileRepo, bindingRepo, cacheService);

        // Save all files to the repository
        for (KnowledgeFile f : files) {
            fileRepo.saveFile(f);
        }

        // Bind each file to the skill
        for (KnowledgeFile f : files) {
            service.bindFileToSkill(skillId, f.id());
        }

        // Retrieve files for the skill
        List<KnowledgeFile> result = service.getFilesForSkill(skillId);

        // Verify: result contains all bound files
        Set<String> expectedIds = files.stream().map(KnowledgeFile::id).collect(Collectors.toSet());
        Set<String> actualIds = result.stream().map(KnowledgeFile::id).collect(Collectors.toSet());

        assertEquals(expectedIds.size(), actualIds.size(),
                "Number of bound files should match");
        assertEquals(expectedIds, actualIds,
                "All bound file IDs should be present in getFilesForSkill result");
    }

    // ---- Arbitrary Providers ----

    @Provide
    Arbitrary<String> skillIds() {
        return Arbitraries.strings().ofMinLength(1).ofMaxLength(36).alpha().numeric();
    }

    @Provide
    Arbitrary<List<KnowledgeFile>> knowledgeFileLists() {
        return knowledgeFiles().list().ofMinSize(1).ofMaxSize(5)
                .filter(list -> list.stream().map(KnowledgeFile::id).distinct().count() == list.size());
    }

    @Provide
    Arbitrary<KnowledgeFile> knowledgeFiles() {
        Arbitrary<String> ids = Arbitraries.strings().ofMinLength(5).ofMaxLength(36).alpha().numeric();
        Arbitrary<String> names = Arbitraries.of("doc.txt", "guide.md", "manual.pdf", "faq.txt", "notes.md");
        Arbitrary<String> texts = Arbitraries.strings().ofMinLength(1).ofMaxLength(100).alpha();

        return Combinators.combine(ids, names, texts)
                .as((id, name, text) -> new KnowledgeFile(
                        id, name, KnowledgeBaseServiceImpl.getExtension(name),
                        100L, "upload", text,
                        System.currentTimeMillis(), System.currentTimeMillis()));
    }
}
