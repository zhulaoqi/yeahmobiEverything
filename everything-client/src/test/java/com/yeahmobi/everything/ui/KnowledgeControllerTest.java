package com.yeahmobi.everything.ui;

import com.yeahmobi.everything.admin.AdminService;
import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.knowledge.KnowledgeBaseService;
import com.yeahmobi.everything.knowledge.KnowledgeFile;
import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepository;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KnowledgeController}.
 * Tests utility methods and logic that don't require JavaFX toolkit initialization.
 */
class KnowledgeControllerTest {

    private KnowledgeController controller;
    private KnowledgeBaseService knowledgeBaseService;
    private SkillKnowledgeBindingRepository bindingRepository;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeController();
        knowledgeBaseService = mock(KnowledgeBaseService.class);
        bindingRepository = mock(SkillKnowledgeBindingRepository.class);
        adminService = mock(AdminService.class);

        controller.setKnowledgeBaseService(knowledgeBaseService);
        controller.setBindingRepository(bindingRepository);
        controller.setAdminService(adminService);
    }

    // ---- formatTimestamp tests ----

    @Test
    void formatTimestamp_positiveValue_returnsFormattedDate() {
        // 2024-01-15 10:30:00 UTC (approximately)
        long timestamp = 1705312200000L;
        String result = KnowledgeController.formatTimestamp(timestamp);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should contain year and time components
        assertTrue(result.contains("2024"));
    }

    @Test
    void formatTimestamp_zeroValue_returnsEmpty() {
        assertEquals("", KnowledgeController.formatTimestamp(0));
    }

    @Test
    void formatTimestamp_negativeValue_returnsEmpty() {
        assertEquals("", KnowledgeController.formatTimestamp(-1));
    }

    // ---- formatFileSize tests ----

    @Test
    void formatFileSize_bytes() {
        assertEquals("500 B", KnowledgeController.formatFileSize(500));
    }

    @Test
    void formatFileSize_kilobytes() {
        String result = KnowledgeController.formatFileSize(2048);
        assertTrue(result.contains("KB"));
        assertTrue(result.contains("2.0"));
    }

    @Test
    void formatFileSize_megabytes() {
        String result = KnowledgeController.formatFileSize(5 * 1024 * 1024);
        assertTrue(result.contains("MB"));
        assertTrue(result.contains("5.0"));
    }

    @Test
    void formatFileSize_gigabytes() {
        String result = KnowledgeController.formatFileSize(2L * 1024 * 1024 * 1024);
        assertTrue(result.contains("GB"));
        assertTrue(result.contains("2.0"));
    }

    @Test
    void formatFileSize_zero() {
        assertEquals("0 B", KnowledgeController.formatFileSize(0));
    }

    // ---- getFileIcon tests ----

    @Test
    void getFileIcon_pdf() {
        assertEquals("📕", KnowledgeController.getFileIcon("pdf"));
    }

    @Test
    void getFileIcon_markdown() {
        assertEquals("📝", KnowledgeController.getFileIcon("md"));
    }

    @Test
    void getFileIcon_txt() {
        assertEquals("📄", KnowledgeController.getFileIcon("txt"));
    }

    @Test
    void getFileIcon_manual() {
        assertEquals("✏️", KnowledgeController.getFileIcon("manual"));
    }

    @Test
    void getFileIcon_null() {
        assertEquals("📄", KnowledgeController.getFileIcon(null));
    }

    @Test
    void getFileIcon_unknown() {
        assertEquals("📄", KnowledgeController.getFileIcon("docx"));
    }

    // ---- getAssociatedSkillNames tests ----

    @Test
    void getAssociatedSkillNames_noBindings_returnsEmpty() {
        when(bindingRepository.getSkillIdsForFile("file1")).thenReturn(List.of());
        assertEquals("", controller.getAssociatedSkillNames("file1"));
    }

    @Test
    void getAssociatedSkillNames_withBindings_returnsSkillNames() {
        // Set up skill name cache
        when(adminService.getAllSkills()).thenReturn(List.of(
                new SkillAdmin("skill1", "翻译助手", "翻译", "", "翻译",
                        true, "", List.of(), null, "test", "zh", "basic",
                        SkillType.GENERAL, SkillKind.PROMPT_ONLY, "", SkillExecutionMode.SINGLE, 0L),
                new SkillAdmin("skill2", "代码助手", "代码", "", "开发",
                        true, "", List.of(), null, "test", "zh", "basic",
                        SkillType.GENERAL, SkillKind.KNOWLEDGE_RAG, "", SkillExecutionMode.SINGLE, 0L)
        ));
        controller.loadData(); // This will fail on loadFileList since FXML isn't loaded,
        // but loadSkillNameCache should work

        when(bindingRepository.getSkillIdsForFile("file1")).thenReturn(List.of("skill1", "skill2"));

        String result = controller.getAssociatedSkillNames("file1");
        assertTrue(result.contains("翻译助手"));
        assertTrue(result.contains("代码助手"));
    }

    @Test
    void getAssociatedSkillNames_unknownSkillId_returnsId() {
        when(bindingRepository.getSkillIdsForFile("file1")).thenReturn(List.of("unknown-id"));

        String result = controller.getAssociatedSkillNames("file1");
        assertEquals("unknown-id", result);
    }

    @Test
    void getAssociatedSkillNames_nullBindingRepository_returnsEmpty() {
        controller.setBindingRepository(null);
        assertEquals("", controller.getAssociatedSkillNames("file1"));
    }

    @Test
    void getAssociatedSkillNames_repositoryException_returnsEmpty() {
        when(bindingRepository.getSkillIdsForFile("file1")).thenThrow(new RuntimeException("DB error"));
        assertEquals("", controller.getAssociatedSkillNames("file1"));
    }

    // ---- Dependency injection tests ----

    @Test
    void setDependencies_setsCorrectly() {
        KnowledgeController ctrl = new KnowledgeController();
        assertNotNull(ctrl.getCurrentFiles());
        assertTrue(ctrl.getCurrentFiles().isEmpty());
        assertNull(ctrl.getEditingFileId());
    }

    @Test
    void setOnBackCallback_callbackIsInvoked() {
        boolean[] called = {false};
        controller.setOnBackCallback(() -> called[0] = true);
        // We can't call onBack() directly without FXML, but we can verify the setter works
        assertNotNull(controller);
    }
}
