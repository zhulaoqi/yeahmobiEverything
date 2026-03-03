package com.yeahmobi.everything.agentscope.tools;

import io.agentscope.core.tool.Toolkit;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Toolkit for generating DOCX documents.
 * Provides methods to create Word documents with text, headings, and basic formatting.
 * <p>
 * NOTE: This is a simplified implementation for AgentScope demonstration.
 * The actual tool registration mechanism depends on the agentscope-java library version.
 * For now, this serves as a placeholder that can be extended based on the actual API.
 * </p>
 */
public class DocxGeneratorTool extends Toolkit {

    private static final String OUTPUT_DIR = System.getProperty("user.home") + "/Downloads/EverythingSkills";

    public DocxGeneratorTool() {
        super();
    }

    /**
     * Generates a Word document (.docx format)
     *
     * @param title    document title
     * @param content  document content (supports multi-line text)
     * @param filename filename (without extension), auto-generated if null
     * @return success message with file path, or error message
     */
    public String generateDocx(String title, String content, String filename) {
        try {
            // Ensure output directory exists
            Path outputPath = Paths.get(OUTPUT_DIR);
            Files.createDirectories(outputPath);

            // Generate filename
            String actualFilename = filename != null && !filename.isBlank() 
                ? filename 
                : "document_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            actualFilename = actualFilename.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_") + ".docx";

            Path docxPath = outputPath.resolve(actualFilename);

            // Create basic DOCX structure
            createBasicDocx(docxPath.toString(), title, content);

            return "✅ 文档生成成功！\n文件路径: " + docxPath.toAbsolutePath();
        } catch (Exception e) {
            return "❌ 文档生成失败: " + e.getMessage();
        }
    }

    /**
     * Lists all generated documents
     *
     * @return list of generated files with paths
     */
    public String listGeneratedFiles() {
        try {
            Path outputPath = Paths.get(OUTPUT_DIR);
            if (!Files.exists(outputPath)) {
                return "暂无生成的文档";
            }

            List<Path> files = Files.list(outputPath)
                    .filter(p -> p.toString().endsWith(".docx"))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .limit(10)
                    .toList();

            if (files.isEmpty()) {
                return "暂无生成的文档";
            }

            StringBuilder result = new StringBuilder("最近生成的文档：\n\n");
            for (int i = 0; i < files.size(); i++) {
                Path file = files.get(i);
                result.append(i + 1).append(". ")
                      .append(file.getFileName())
                      .append("\n");
            }
            result.append("\n文件夹路径: ").append(outputPath.toAbsolutePath());

            return result.toString();
        } catch (Exception e) {
            return "❌ 列表获取失败: " + e.getMessage();
        }
    }

    /**
     * Creates a basic DOCX file using a minimal ZIP structure.
     * This is a simplified implementation without Apache POI dependency.
     */
    private void createBasicDocx(String filepath, String title, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(filepath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // 1. [Content_Types].xml
            addZipEntry(zos, "[Content_Types].xml", buildContentTypes());

            // 2. _rels/.rels
            addZipEntry(zos, "_rels/.rels", buildRelsXml());

            // 3. word/document.xml
            addZipEntry(zos, "word/document.xml", buildDocumentXml(title, content));

            // 4. word/_rels/document.xml.rels
            addZipEntry(zos, "word/_rels/document.xml.rels", buildDocumentRelsXml());
        }
    }

    private void addZipEntry(ZipOutputStream zos, String entryName, String content) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    private String buildContentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n" +
                "  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n" +
                "  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n" +
                "  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n" +
                "</Types>";
    }

    private String buildRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>\n" +
                "</Relationships>";
    }

    private String buildDocumentRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n" +
                "</Relationships>";
    }

    private String buildDocumentXml(String title, String content) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
        xml.append("  <w:body>\n");

        // Add title as heading
        if (title != null && !title.isBlank()) {
            xml.append("    <w:p>\n");
            xml.append("      <w:pPr>\n");
            xml.append("        <w:pStyle w:val=\"Heading1\"/>\n");
            xml.append("      </w:pPr>\n");
            xml.append("      <w:r>\n");
            xml.append("        <w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr>\n");
            xml.append("        <w:t>").append(escapeXml(title)).append("</w:t>\n");
            xml.append("      </w:r>\n");
            xml.append("    </w:p>\n");
        }

        // Add content paragraphs
        if (content != null) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                xml.append("    <w:p>\n");
                xml.append("      <w:r>\n");
                xml.append("        <w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t>\n");
                xml.append("      </w:r>\n");
                xml.append("    </w:p>\n");
            }
        }

        xml.append("  </w:body>\n");
        xml.append("</w:document>");
        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
