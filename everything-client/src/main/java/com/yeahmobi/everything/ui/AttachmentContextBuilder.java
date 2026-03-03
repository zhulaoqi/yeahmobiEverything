package com.yeahmobi.everything.ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Builds compact attachment context for chat requests.
 * <p>
 * MVP support:
 * - Plain text formats: txt/md/csv/json/log/xml/yaml/yml
 * - Office formats: docx/xlsx (lightweight XML extraction)
 * - PDF: basic byte-text fallback extraction
 * - Code files: java/py/js/ts/sql
 * - Images: metadata only (OCR is intentionally deferred)
 * </p>
 */
final class AttachmentContextBuilder {

    private static final int MAX_FILES = 5;
    private static final int MAX_CHARS_PER_FILE = 3000;
    private static final int MAX_TOTAL_CHARS = 10000;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private AttachmentContextBuilder() {
    }

    static AttachmentPayload buildPayload(String userMessage, List<File> attachments) {
        String base = userMessage != null ? userMessage : "";
        if (attachments == null || attachments.isEmpty()) {
            return new AttachmentPayload(base, "", "", List.of());
        }
        List<File> files = attachments.stream()
                .filter(f -> f != null && f.exists() && f.isFile())
                .limit(MAX_FILES)
                .toList();
        if (files.isEmpty()) {
            return new AttachmentPayload(base, "", "", List.of());
        }

        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n【附件上下文】\n");
        int used = 0;
        int index = 1;
        List<AttachmentItem> items = new ArrayList<>();
        int parsedCount = 0;
        int truncatedCount = 0;
        int anomalyHintCount = 0;
        int unsupportedCount = 0;
        for (File file : files) {
            int remain = MAX_TOTAL_CHARS - used;
            if (remain <= 0) {
                break;
            }
            ParseResult parsed = buildSingleFileBlock(file, remain);
            if (parsed.content().isBlank()) {
                continue;
            }
            parsedCount++;
            if (parsed.status() != null && parsed.status().contains("截断")) {
                truncatedCount++;
            }
            if (parsed.status() != null && parsed.status().contains("未支持")) {
                unsupportedCount++;
            }
            if (parsed.content().contains("异常提示:")) {
                anomalyHintCount++;
            }
            sb.append("\n---\n");
            sb.append("附件").append(index).append("：").append(file.getName()).append("\n");
            sb.append(parsed.content()).append("\n");
            used += parsed.content().length();
            items.add(new AttachmentItem(file, parsed.status(), parsed.detail()));
            index++;
        }
        if (parsedCount > 0) {
            sb.append("\n【批量附件总览】\n");
            sb.append("- 本次处理附件: ").append(parsedCount).append(" / ").append(files.size()).append("\n");
            sb.append("- 发现异常提示的附件: ").append(anomalyHintCount).append("\n");
            sb.append("- 内容被截断的附件: ").append(truncatedCount).append("\n");
            sb.append("- 暂不支持自动解析的附件: ").append(unsupportedCount).append("\n");
            sb.append("- 建议优先动作: 先查看“异常提示”与“关键信息抽取”字段，再决定需要深挖的附件。");
        }
        sb.append("\n请优先基于“附件上下文”回答；若附件信息不足，请明确指出缺失项。");
        String batchSummary = "已解析 " + parsedCount + "/" + files.size()
                + " | 异常 " + anomalyHintCount
                + " | 截断 " + truncatedCount
                + " | 未支持 " + unsupportedCount;
        return new AttachmentPayload(sb.toString(), buildDisplaySuffixInternal(files), batchSummary, items);
    }

    static String buildRequestMessage(String userMessage, List<File> attachments) {
        return buildPayload(userMessage, attachments).requestMessage();
    }

    static String buildDisplaySuffix(List<File> attachments) {
        return buildPayload("", attachments).displaySuffix();
    }

    private static String buildDisplaySuffixInternal(List<File> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (File f : attachments) {
            if (f != null && f.getName() != null && !f.getName().isBlank()) {
                names.add(f.getName());
            }
            if (names.size() >= 3) {
                break;
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        return "\n\n[附件] " + String.join(", ", names) + (attachments.size() > names.size() ? " ..." : "");
    }

    private static ParseResult buildSingleFileBlock(File file, int remainBudget) {
        String ext = extension(file.getName());
        long sizeKb = Math.max(1, file.length() / 1024);
        String meta = "类型: " + (ext.isBlank() ? "unknown" : ext) + ", 大小: " + sizeKb + "KB";
        if (isImage(ext)) {
            String ocr = extractImageOcrText(file);
            if (ocr == null || ocr.isBlank()) {
                return new ParseResult(
                        meta + "\n内容: 图片文件（未检测到 OCR 能力，请结合你的问题描述图片关键信息）",
                        "图片(未OCR)",
                        "建议安装 tesseract 以启用截图识别"
                );
            }
            return withTruncation(meta, ocr, remainBudget, "图片OCR", "图片文字已识别", ext, file.getName());
        }
        if ("docx".equals(ext)) {
            String text = extractDocxText(file);
            return withTruncation(meta, text, remainBudget, "DOCX", "文档内容已抽取", ext, file.getName());
        }
        if ("xlsx".equals(ext)) {
            String text = extractXlsxText(file);
            return withTruncation(meta, text, remainBudget, "XLSX", "表格内容已抽取", ext, file.getName());
        }
        if ("pdf".equals(ext)) {
            String text = extractPdfText(file);
            return withTruncation(meta, text, remainBudget, "PDF", "PDF 内容已抽取(基础模式)", ext, file.getName());
        }
        if (!isTextLike(ext)) {
            return new ParseResult(
                    meta + "\n内容: 当前版本暂不支持自动解析该文件类型，请提供关键信息或导出为 txt/csv/md。",
                    "未支持",
                    "请转换为 txt/csv/md/pdf/docx/xlsx"
            );
        }
        String text = safeReadText(file);
        return withTruncation(meta, text, remainBudget, "文本", "文本内容已抽取", ext, file.getName());
    }

    private static ParseResult withTruncation(
            String meta, String text, int remainBudget, String statusPrefix, String detail, String ext, String fileName
    ) {
        if (text.isBlank()) {
            return new ParseResult(
                    meta + "\n内容: 文件为空或无法读取文本内容。",
                    statusPrefix + "(空)",
                    "未提取到有效文本"
            );
        }
        int maxChars = Math.min(MAX_CHARS_PER_FILE, Math.max(500, remainBudget));
        boolean truncated = false;
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars) + "\n...[内容已截断]";
            truncated = true;
        }
        String status = statusPrefix + (truncated ? "(截断)" : "");
        String details = detail + "，约 " + text.length() + " 字";
        String insights = buildStructuredInsights(text, ext, fileName);
        String anomalyHints = buildAnomalyHints(text, ext);
        StringBuilder content = new StringBuilder(meta);
        if (!insights.isBlank()) {
            content.append("\n结构化要点:\n").append(insights);
        }
        if (!anomalyHints.isBlank()) {
            content.append("\n异常提示:\n").append(anomalyHints);
        }
        content.append("\n内容:\n").append(text);
        return new ParseResult(content.toString(), status, details);
    }

    private static String buildStructuredInsights(String text, String ext, String fileName) {
        String loweredExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if ("csv".equals(loweredExt) || "xlsx".equals(loweredExt)) {
            return buildTableInsights(text);
        }
        if ("pdf".equals(loweredExt) || "docx".equals(loweredExt)) {
            return buildDocumentInsights(text, fileName);
        }
        return "";
    }

    private static String buildTableInsights(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\\R");
        int rowCount = 0;
        int nonBlank = 0;
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                nonBlank++;
            }
            if (nonBlank > 0) {
                rowCount++;
            }
        }
        int numericCount = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0d;
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find() && numericCount < 300) {
            try {
                double v = Double.parseDouble(m.group());
                numericCount++;
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
            } catch (Exception ignored) {
                // ignore malformed number
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("- 估计行数: ").append(Math.max(1, rowCount)).append("\n");
        if (numericCount > 0) {
            double avg = sum / numericCount;
            sb.append("- 数值字段样本: ").append(numericCount).append(" 个")
                    .append("，最小 ").append(formatNum(min))
                    .append("，最大 ").append(formatNum(max))
                    .append("，均值 ").append(formatNum(avg)).append("\n");
        }
        sb.append("- 建议输出: 异常值清单 + 分组对比 + 下一步动作");
        return sb.toString();
    }

    private static String buildDocumentInsights(String text, String fileName) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String partyA = matchFirst(text, "(甲方|party\\s*a)[:：\\s]*([^\\n，。;；]{2,40})");
        String partyB = matchFirst(text, "(乙方|party\\s*b)[:：\\s]*([^\\n，。;；]{2,40})");
        String amount = matchFirst(text, "(金额|总价|price|amount)[:：\\s]*([\\d,\\.]+)");
        String date = matchFirst(text, "(签订日期|日期|date)[:：\\s]*([0-9]{4}[-/年][0-9]{1,2}[-/月][0-9]{1,2})");
        if (fileName != null && !fileName.isBlank()) {
            sb.append("- 文档名: ").append(fileName).append("\n");
        }
        sb.append("- 关键信息抽取:\n");
        sb.append("  - 甲方: ").append(partyA.isBlank() ? "未识别" : partyA).append("\n");
        sb.append("  - 乙方: ").append(partyB.isBlank() ? "未识别" : partyB).append("\n");
        sb.append("  - 金额: ").append(amount.isBlank() ? "未识别" : amount).append("\n");
        sb.append("  - 日期: ").append(date.isBlank() ? "未识别" : date).append("\n");
        sb.append("- 建议输出: 条款摘要 + 风险点 + 需确认清单");
        return sb.toString();
    }

    private static String buildAnomalyHints(String text, String ext) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String loweredExt = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (!"csv".equals(loweredExt) && !"xlsx".equals(loweredExt)) {
            return "";
        }
        int count = 0;
        int negative = 0;
        int zero = 0;
        double max = -Double.MAX_VALUE;
        double sum = 0d;
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find() && count < 300) {
            try {
                double v = Double.parseDouble(m.group());
                count++;
                if (v < 0) {
                    negative++;
                }
                if (Math.abs(v) < 0.000001d) {
                    zero++;
                }
                max = Math.max(max, v);
                sum += v;
            } catch (Exception ignored) {
                // ignore malformed number
            }
        }
        if (count == 0) {
            return "";
        }
        double avg = sum / count;
        List<String> hints = new ArrayList<>();
        if (negative > 0) {
            hints.add("- 发现负值 " + negative + " 个（需确认是否退款/冲销）");
        }
        if (zero > Math.max(3, count / 5)) {
            hints.add("- 0 值占比偏高（需确认缺失填充或口径）");
        }
        if (max > 0 && avg > 0 && max > avg * 8) {
            hints.add("- 可能存在极端值（最大值明显高于均值）");
        }
        return String.join("\n", hints);
    }

    private static String matchFirst(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
            if (!m.find()) {
                return "";
            }
            if (m.groupCount() >= 2) {
                return m.group(2).trim();
            }
            return m.group(1).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String formatNum(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.000001d) {
            return Long.toString((long) Math.rint(v));
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String safeReadText(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractDocxText(File file) {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    return stripXmlTags(xml);
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        return safeReadText(file);
    }

    private static String extractXlsxText(File file) {
        StringBuilder sb = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file.toPath()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null) {
                    continue;
                }
                if (name.startsWith("xl/sharedStrings") || name.startsWith("xl/worksheets/sheet")) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    String text = stripXmlTags(xml);
                    if (!text.isBlank()) {
                        if (!sb.isEmpty()) {
                            sb.append("\n");
                        }
                        sb.append("[").append(name).append("]\n").append(text);
                    }
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }
        if (!sb.isEmpty()) {
            return sb.toString();
        }
        return safeReadText(file);
    }

    private static String extractPdfText(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String raw = new String(bytes, StandardCharsets.UTF_8);
            // Lightweight cleanup for binary-ish PDF bytes.
            String cleaned = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ");
            return cleaned.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractImageOcrText(File file) {
        try {
            Process process = new ProcessBuilder("tesseract",
                    file.getAbsolutePath(), "stdout", "-l", "chi_sim+eng")
                    .redirectErrorStream(true)
                    .start();
            boolean done = process.waitFor(8, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return "";
            }
            byte[] output = process.getInputStream().readAllBytes();
            String text = new String(output, StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                return "";
            }
            return text;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String stripXmlTags(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }
        String text = xml
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String extension(String name) {
        if (name == null) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx >= name.length() - 1) {
            return "";
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isImage(String ext) {
        return "png".equals(ext) || "jpg".equals(ext) || "jpeg".equals(ext)
                || "gif".equals(ext) || "webp".equals(ext);
    }

    private static boolean isTextLike(String ext) {
        return "txt".equals(ext) || "md".equals(ext) || "csv".equals(ext)
                || "json".equals(ext) || "log".equals(ext) || "xml".equals(ext)
                || "yaml".equals(ext) || "yml".equals(ext) || "java".equals(ext)
                || "py".equals(ext) || "js".equals(ext) || "ts".equals(ext)
                || "sql".equals(ext) || "pdf".equals(ext) || "docx".equals(ext)
                || "xlsx".equals(ext);
    }

    static final class AttachmentPayload {
        private final String requestMessage;
        private final String displaySuffix;
        private final String batchSummary;
        private final List<AttachmentItem> items;

        AttachmentPayload(String requestMessage, String displaySuffix, String batchSummary, List<AttachmentItem> items) {
            this.requestMessage = requestMessage;
            this.displaySuffix = displaySuffix;
            this.batchSummary = batchSummary;
            this.items = items != null ? items : List.of();
        }

        String requestMessage() {
            return requestMessage;
        }

        String displaySuffix() {
            return displaySuffix;
        }

        String batchSummary() {
            return batchSummary;
        }

        List<AttachmentItem> items() {
            return items;
        }
    }

    static final class AttachmentItem {
        private final File file;
        private final String status;
        private final String detail;

        AttachmentItem(File file, String status, String detail) {
            this.file = file;
            this.status = status;
            this.detail = detail;
        }

        File file() {
            return file;
        }

        String status() {
            return status;
        }

        String detail() {
            return detail;
        }
    }

    private record ParseResult(String content, String status, String detail) {
    }
}
