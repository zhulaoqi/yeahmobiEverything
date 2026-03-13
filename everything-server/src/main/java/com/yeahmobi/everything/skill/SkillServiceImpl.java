package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.NetworkException;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.local.ChatRepository;
import com.yeahmobi.everything.repository.local.FavoriteRepository;
import com.yeahmobi.everything.repository.local.UsageRepository;
import com.yeahmobi.everything.repository.mysql.SkillRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SkillService}.
 * <p>
 * Fetches Skills from Redis cache first; if the cache is empty, queries
 * the MySQL database via {@link SkillRepository} and caches the result.
 * Provides search, category filtering, type filtering, and default Skills.
 * </p>
 * <p>
 * Favorites and usage tracking methods delegate to {@link FavoriteRepository}
 * and {@link UsageRepository} respectively.
 * </p>
 */
public class SkillServiceImpl implements SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillServiceImpl.class);

    /** Cache TTL for Skill list: 10 minutes */
    static final long SKILL_CACHE_TTL_SECONDS = 600;

    private final SkillRepository skillRepository;
    private final CacheService cacheService;
    private final FavoriteRepository favoriteRepository;
    private final UsageRepository usageRepository;
    private final ChatRepository chatRepository;

    /**
     * Creates a SkillServiceImpl with the given repository and cache service.
     *
     * @param skillRepository    the MySQL skill repository
     * @param cacheService       the Redis cache service
     * @param favoriteRepository the local SQLite favorite repository
     * @param usageRepository    the local SQLite usage repository
     * @param chatRepository     the local SQLite chat repository
     */
    public SkillServiceImpl(SkillRepository skillRepository, CacheService cacheService,
                            FavoriteRepository favoriteRepository, UsageRepository usageRepository,
                            ChatRepository chatRepository) {
        this.skillRepository = skillRepository;
        this.cacheService = cacheService;
        this.favoriteRepository = favoriteRepository;
        this.usageRepository = usageRepository;
        this.chatRepository = chatRepository;
    }

    @Override
    public List<Skill> fetchSkills() throws NetworkException {
        // 1. Check Redis cache first
        try {
            Optional<List<Skill>> cached = cacheService.getCachedSkillList();
            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (Exception e) {
            log.warn("Failed to read skill cache, falling back to database", e);
        }

        // 2. Query MySQL via SkillRepository
        try {
            List<SkillAdmin> adminSkills = skillRepository.getAllSkills();
            List<Skill> skills = adminSkills.stream()
                    .filter(SkillAdmin::enabled)
                    .map(this::toSkill)
                    .collect(Collectors.toList());

            // 3. Cache the result
            try {
                cacheService.cacheSkillList(skills, SKILL_CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("Failed to cache skill list", e);
            }

            return skills;
        } catch (Exception e) {
            throw new NetworkException("Failed to fetch skills from database", e);
        }
    }

    @Override
    public List<Skill> searchSkills(String keyword, List<Skill> allSkills) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>(allSkills);
        }
        String lowerKeyword = keyword.toLowerCase();
        return allSkills.stream()
                .filter(skill -> {
                    String name = skill.name() != null ? skill.name().toLowerCase() : "";
                    String description = skill.description() != null ? skill.description().toLowerCase() : "";
                    return name.contains(lowerKeyword) || description.contains(lowerKeyword);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> filterByCategory(String category, List<Skill> allSkills) {
        if (category == null || category.isBlank()) {
            return new ArrayList<>(allSkills);
        }
        String normalized = category.trim().toLowerCase();
        return allSkills.stream()
                .filter(skill -> {
                    String skillCategory = skill.category() != null ? skill.category().trim().toLowerCase() : "";
                    return normalized.equals(skillCategory);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> filterByType(SkillType type, List<Skill> allSkills) {
        if (type == null) {
            return new ArrayList<>(allSkills);
        }
        return allSkills.stream()
                .filter(skill -> type.equals(skill.type()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Skill> getFavorites(String userId) {
        List<String> favoriteIds = favoriteRepository.getFavoriteSkillIds(userId);
        if (favoriteIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Skill> allSkills = fetchSkills();
            return allSkills.stream()
                    .filter(skill -> favoriteIds.contains(skill.id()))
                    .collect(Collectors.toList());
        } catch (NetworkException e) {
            log.warn("Failed to fetch skills for favorites", e);
            return List.of();
        }
    }

    @Override
    public void toggleFavorite(String userId, String skillId) {
        if (favoriteRepository.isFavorite(userId, skillId)) {
            favoriteRepository.removeFavorite(userId, skillId);
        } else {
            favoriteRepository.addFavorite(userId, skillId);
        }
    }

    @Override
    public boolean isFavorite(String userId, String skillId) {
        return favoriteRepository.isFavorite(userId, skillId);
    }

    @Override
    public List<Skill> getUsedSkills(String userId) {
        List<String> usedIds = chatRepository.getUsedSkillIds(userId);
        if (usedIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Skill> allSkills = fetchSkills();
            return allSkills.stream()
                    .filter(skill -> usedIds.contains(skill.id()))
                    .collect(Collectors.toList());
        } catch (NetworkException e) {
            log.warn("Failed to fetch used skills", e);
            return List.of();
        }
    }

    @Override
    public List<Skill> getRecentlyUsed(String userId, int limit) {
        List<String> recentIds = usageRepository.getRecentSkillIds(userId, limit);
        if (recentIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Skill> allSkills = fetchSkills();
            // Preserve the order from recentIds (most recent first)
            return recentIds.stream()
                    .map(id -> allSkills.stream()
                            .filter(skill -> skill.id().equals(id))
                            .findFirst()
                            .orElse(null))
                    .filter(skill -> skill != null)
                    .collect(Collectors.toList());
        } catch (NetworkException e) {
            log.warn("Failed to fetch skills for recently used", e);
            return List.of();
        }
    }

    @Override
    public void recordUsage(String userId, String skillId) {
        usageRepository.recordUsage(userId, skillId);
    }

    @Override
    public int getUsageCount(String userId, String skillId) {
        return usageRepository.getUsageCount(userId, skillId);
    }

    @Override
    public List<Skill> getDefaultSkills() {
        List<Skill> defaults = new ArrayList<>();

        defaults.add(new Skill(
                "default-translate",
                "翻译助手",
                "支持多语言互译，精准翻译各类文本内容",
                "translate.png",
                "翻译",
                true,
                "输入需要翻译的文本，指定目标语言即可获得翻译结果。支持中英日韩等多种语言。",
                List.of("请将以下内容翻译成英文：今天天气很好", "Translate to Chinese: Hello, how are you?"),
                null,
                "default",
                "zh",
                "basic",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "你是一个专业的翻译助手。请将用户输入的文本翻译成目标语言，保持原文的语气和风格。如果用户没有指定目标语言，默认翻译成中文。",
                SkillExecutionMode.SINGLE
        ));

        defaults.add(new Skill(
                "default-copywriting",
                "文案撰写",
                "专业文案撰写助手，帮助创作各类营销文案和商务文档",
                "copywriting.png",
                "写作",
                true,
                "描述你需要的文案类型、目标受众和关键信息，AI 将为你生成专业文案。",
                List.of("帮我写一段产品推广文案，产品是智能手表", "撰写一封商务邀请邮件"),
                null,
                "default",
                "zh",
                "basic",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "你是一个专业的文案撰写助手。根据用户的需求，创作高质量的文案内容。注意文案的吸引力、专业性和目标受众的匹配度。",
                SkillExecutionMode.SINGLE
        ));

        defaults.add(new Skill(
                "default-code",
                "代码助手",
                "编程开发助手，支持代码编写、调试和优化",
                "code.png",
                "开发",
                true,
                "描述你的编程需求或粘贴需要优化的代码，AI 将提供代码解决方案。",
                List.of("用 Java 写一个快速排序算法", "帮我优化这段 Python 代码的性能"),
                null,
                "default",
                "zh",
                "basic",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "你是一个专业的编程助手。帮助用户编写、调试和优化代码。提供清晰的代码注释和解释。支持多种编程语言。",
                SkillExecutionMode.SINGLE
        ));

        defaults.add(new Skill(
                "default-data-analysis",
                "数据分析",
                "数据分析助手，帮助解读数据、生成报告和可视化建议",
                "data.png",
                "数据",
                true,
                "提供你的数据或描述分析需求，AI 将帮助你进行数据分析和解读。",
                List.of("分析这组销售数据的趋势", "帮我设计一个数据分析方案"),
                null,
                "default",
                "zh",
                "basic",
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                "你是一个专业的数据分析助手。帮助用户分析数据、发现趋势、生成报告。提供清晰的数据解读和可视化建议。",
                SkillExecutionMode.SINGLE
        ));

        return defaults;
    }

    // ---- Internal helpers ----

    /**
     * Converts a SkillAdmin record to a Skill record.
     * <p>
     * SkillAdmin carries usageGuide/examples and localized metadata,
     * which are passed through to Skill for client display.
     * </p>
     *
     * @param admin the SkillAdmin record
     * @return the corresponding Skill record
     */
    Skill toSkill(SkillAdmin admin) {
        return new Skill(
                admin.id(),
                admin.name(),
                admin.description(),
                admin.icon(),
                admin.category(),
                admin.enabled(),
                admin.usageGuide(),
                admin.examples(),
                admin.i18nJson(),
                admin.source(),
                admin.sourceLang(),
                admin.qualityTier(),
                admin.toolIds(),
                admin.toolGroups(),
                admin.contextPolicy(),
                admin.type(),
                admin.kind(),
                admin.promptTemplate(),
                admin.executionMode()
        );
    }
}
