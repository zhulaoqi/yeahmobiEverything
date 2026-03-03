package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.SkillPackageRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Secure installer for skill ZIP packages.
 */
public class SkillZipInstaller {

    private static final long MAX_ZIP_SIZE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_UNZIPPED_SIZE_BYTES = 80L * 1024 * 1024;
    private static final int MAX_FILE_COUNT = 500;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".md", ".txt", ".json", ".yaml", ".yml", ".py", ".sh", ".js", ".ts",
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".csv", ".xml", ".html", ".css",
            ".svg", ".xsd", ".toml", ".ini", ".cfg", ".conf", ".log"
    );
    private static final Set<String> ALLOWED_NO_EXT_FILENAMES = Set.of(
            "license", "license.txt", "license.md",
            "notice", "readme", "makefile"
    );

    private final SkillRepository skillRepository;
    private final SkillPackageRepository packageRepository;
    private final CacheService cacheService;

    public SkillZipInstaller(SkillRepository skillRepository,
                             SkillPackageRepository packageRepository,
                             CacheService cacheService) {
        this.skillRepository = skillRepository;
        this.packageRepository = packageRepository;
        this.cacheService = cacheService;
    }

    public SkillZipInstallResult installFromZip(Path zipPath, String sourceUrl, String actor) {
        if (zipPath == null || !Files.exists(zipPath)) {
            return SkillZipInstallResult.fail(null, null, null, "ZIP 文件不存在");
        }
        String name = zipPath.getFileName().toString().toLowerCase();
        if (!name.endsWith(".zip")) {
            return SkillZipInstallResult.fail(null, null, null, "仅支持 .zip 包");
        }

        try {
            long size = Files.size(zipPath);
            if (size <= 0 || size > MAX_ZIP_SIZE_BYTES) {
                return SkillZipInstallResult.fail(null, null, null, "ZIP 大小不合法或超限(20MB)");
            }

            String sha256 = sha256(zipPath);
            Path extractRoot = Files.createTempDirectory("skill-zip-install-");
            try {
                ExtractionStats stats = secureExtract(zipPath, extractRoot);
                if (stats.fileCount > MAX_FILE_COUNT) {
                    return SkillZipInstallResult.fail(null, null, sha256, "文件数量超限");
                }
                if (stats.totalBytes > MAX_UNZIPPED_SIZE_BYTES) {
                    return SkillZipInstallResult.fail(null, null, sha256, "解压后体积超限");
                }

                Path skillMd = findSkillMd(extractRoot);
                if (skillMd == null) {
                    return SkillZipInstallResult.fail(null, null, sha256, "缺少 SKILL.md");
                }

                String content = Files.readString(skillMd, StandardCharsets.UTF_8);
                String defaultName = skillMd.getParent().getFileName().toString();
                AnthropicSkillImporter parser = new AnthropicSkillImporter(skillRepository, cacheService);
                SkillManifest manifest = parser.parseSkillMarkdown(content, defaultName, "zip");
                String skillName = manifest.name();
                String skillVersion = parseVersion(content).orElse("1.0.0");

                Optional<SkillPackageRepository.SkillPackageRecord> existing =
                        packageRepository.findByNameAndVersion(skillName, skillVersion);
                if (existing.isPresent()) {
                    SkillPackageRepository.SkillPackageRecord e = existing.get();
                    if (sha256.equalsIgnoreCase(e.artifactSha256())) {
                        packageRepository.insertAudit(new SkillPackageRepository.SkillPackageAuditRecord(
                                UUID.randomUUID().toString(),
                                e.id(),
                                skillName,
                                skillVersion,
                                sha256,
                                "IDEMPOTENT_REUPLOAD",
                                actor,
                                "same skill+version+hash"
                        ));
                        return SkillZipInstallResult.idempotent(skillName, skillVersion, sha256, "同版本同哈希，已安装");
                    }
                    packageRepository.insertAudit(new SkillPackageRepository.SkillPackageAuditRecord(
                            UUID.randomUUID().toString(),
                            e.id(),
                            skillName,
                            skillVersion,
                            sha256,
                            "REJECT_CONFLICT",
                            actor,
                            "same skill+version but different hash"
                    ));
                    return SkillZipInstallResult.fail(skillName, skillVersion, sha256, "同 skill/version 哈希冲突，拒绝覆盖");
                }

                int changed = parser.importFromPath(extractRoot.toString());

                String packageId = UUID.randomUUID().toString();
                packageRepository.upsertInstalled(new SkillPackageRepository.SkillPackageRecord(
                        packageId,
                        skillName,
                        skillVersion,
                        sha256,
                        sourceUrl,
                        sourceUrl == null || sourceUrl.isBlank() ? "upload" : "download",
                        "INSTALLED",
                        true,
                        "import changed=" + changed
                ));
                packageRepository.deactivateOtherVersions(skillName, packageId);
                packageRepository.insertAudit(new SkillPackageRepository.SkillPackageAuditRecord(
                        UUID.randomUUID().toString(),
                        packageId,
                        skillName,
                        skillVersion,
                        sha256,
                        "INSTALLED",
                        actor,
                        "import changed=" + changed
                ));
                return SkillZipInstallResult.success(skillName, skillVersion, sha256, "安装成功，变更条数: " + changed);
            } finally {
                deleteRecursively(extractRoot);
            }
        } catch (Exception e) {
            return SkillZipInstallResult.fail(null, null, null, "安装失败: " + e.getMessage());
        }
    }

    private ExtractionStats secureExtract(Path zipPath, Path targetDir) throws IOException {
        int count = 0;
        long totalBytes = 0;
        try (InputStream fis = Files.newInputStream(zipPath); ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName == null || entryName.isBlank()) {
                    continue;
                }
                Path out = targetDir.resolve(entryName).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IOException("非法路径穿越条目: " + entryName);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }

                validateAllowedFilename(entryName);
                Files.createDirectories(out.getParent());
                long copied = Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                totalBytes += copied;
                count++;
                if (count > MAX_FILE_COUNT || totalBytes > MAX_UNZIPPED_SIZE_BYTES) {
                    break;
                }
            }
        }
        return new ExtractionStats(count, totalBytes);
    }

    private void validateAllowedFilename(String entryName) throws IOException {
        String lower = entryName.toLowerCase();
        if (lower.endsWith("/skill.md") || lower.equals("skill.md")) {
            return;
        }
        int idx = lower.lastIndexOf('.');
        if (idx < 0) {
            String filename = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
            if (!ALLOWED_NO_EXT_FILENAMES.contains(filename)) {
                throw new IOException("不允许无扩展名文件: " + entryName);
            }
            return;
        }
        String ext = lower.substring(idx);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IOException("不允许的文件类型: " + entryName);
        }
    }

    private Path findSkillMd(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase("SKILL.md"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private Optional<String> parseVersion(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Optional.empty();
        }
        if (!markdown.startsWith("---")) {
            return Optional.empty();
        }
        int end = markdown.indexOf("\n---", 3);
        if (end < 0) {
            return Optional.empty();
        }
        String frontmatter = markdown.substring(3, end);
        for (String line : frontmatter.split("\n")) {
            String t = line.trim();
            if (t.startsWith("version:")) {
                String value = t.substring("version:".length()).trim();
                value = value.replace("\"", "").replace("'", "");
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record ExtractionStats(int fileCount, long totalBytes) {}
}

