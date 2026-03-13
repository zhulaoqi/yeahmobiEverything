package com.yeahmobi.everything.personalskill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.notification.FeishuNotifier;
import com.yeahmobi.everything.repository.cache.CacheService;
import com.yeahmobi.everything.repository.mysql.SkillRepository;
import com.yeahmobi.everything.skill.SkillExecutionMode;
import com.yeahmobi.everything.skill.SkillKind;
import com.yeahmobi.everything.skill.SkillType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link PersonalSkillService}.
 */
public class PersonalSkillServiceImpl implements PersonalSkillService {

    private static final Logger log = LoggerFactory.getLogger(PersonalSkillServiceImpl.class);

    private final PersonalSkillRepository repository;
    private final FeishuNotifier feishuNotifier;
    private final SkillRepository skillRepository;
    private final CacheService cacheService;

    public PersonalSkillServiceImpl(PersonalSkillRepository repository,
                                    FeishuNotifier feishuNotifier,
                                    SkillRepository skillRepository,
                                    CacheService cacheService) {
        this.repository = repository;
        this.feishuNotifier = feishuNotifier;
        this.skillRepository = skillRepository;
        this.cacheService = cacheService;
    }

    @Override
    public PersonalSkillResult saveDraft(String userId, String name, String description,
                                         String category, String promptTemplate, String existingId) {
        if (userId == null || userId.isBlank()) {
            return new PersonalSkillResult(false, "用户信息缺失，请重新登录", null);
        }
        String validation = validateSkillPackage(name, description, category, promptTemplate);
        if (validation != null) {
            return new PersonalSkillResult(false, validation, null);
        }

        long now = System.currentTimeMillis();
        if (existingId == null || existingId.isBlank()) {
            PersonalSkill skill = new PersonalSkill(
                    UUID.randomUUID().toString(),
                    userId,
                    name.trim(),
                    description.trim(),
                    category.trim(),
                    promptTemplate.trim(),
                    PersonalSkillStatus.DRAFT,
                    null,
                    now,
                    now
            );
            repository.save(skill);
            return new PersonalSkillResult(true, "草稿已保存", skill);
        }

        Optional<PersonalSkill> existing = repository.getById(existingId);
        if (existing.isEmpty() || !userId.equals(existing.get().userId())) {
            return new PersonalSkillResult(false, "技能不存在或无权限", null);
        }
        PersonalSkill updated = new PersonalSkill(
                existing.get().id(),
                userId,
                name.trim(),
                description.trim(),
                category.trim(),
                promptTemplate.trim(),
                PersonalSkillStatus.DRAFT,
                existing.get().reviewerNote(),
                existing.get().createdAt(),
                now
        );
        repository.update(updated);
        return new PersonalSkillResult(true, "草稿已更新", updated);
    }

    @Override
    public PersonalSkillResult submitForReview(String userId, String skillId) {
        if (userId == null || userId.isBlank()) {
            return new PersonalSkillResult(false, "用户信息缺失，请重新登录", null);
        }
        if (skillId == null || skillId.isBlank()) {
            return new PersonalSkillResult(false, "技能不存在，请先保存草稿", null);
        }
        Optional<PersonalSkill> existing = repository.getById(skillId);
        if (existing.isEmpty() || !userId.equals(existing.get().userId())) {
            return new PersonalSkillResult(false, "技能不存在或无权限", null);
        }
        PersonalSkill current = existing.get();
        String validation = validateSkillPackage(current.name(), current.description(),
                current.category(), current.promptTemplate());
        if (validation != null) {
            return new PersonalSkillResult(false, validation, null);
        }
        PersonalSkill updated = new PersonalSkill(
                current.id(),
                current.userId(),
                current.name(),
                current.description(),
                current.category(),
                current.promptTemplate(),
                PersonalSkillStatus.PENDING,
                current.reviewerNote(),
                current.createdAt(),
                System.currentTimeMillis()
        );
        repository.update(updated);

        if (feishuNotifier != null) {
            try {
                feishuNotifier.sendPersonalSkillReviewNotification(updated);
            } catch (Exception ignored) {
                log.warn("Feishu notification failed for personal skill review, submission continues: {}", ignored.getMessage());
            }
        }
        return new PersonalSkillResult(true, "已提交审核，管理员将尽快评估", updated);
    }

    @Override
    public List<PersonalSkill> listByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        return repository.getByUser(userId);
    }

    @Override
    public List<PersonalSkill> listPending() {
        return repository.getPending();
    }

    @Override
    public PersonalSkillResult reviewSkill(String skillId, PersonalSkillStatus status, String reviewerNote) {
        if (skillId == null || skillId.isBlank()) {
            return new PersonalSkillResult(false, "技能不存在", null);
        }
        if (status == null || status == PersonalSkillStatus.DRAFT) {
            return new PersonalSkillResult(false, "审核状态无效", null);
        }
        Optional<PersonalSkill> existing = repository.getById(skillId);
        if (existing.isEmpty()) {
            return new PersonalSkillResult(false, "技能不存在", null);
        }
        repository.updateStatus(skillId, status, reviewerNote);
        PersonalSkill updated = new PersonalSkill(
                existing.get().id(),
                existing.get().userId(),
                existing.get().name(),
                existing.get().description(),
                existing.get().category(),
                existing.get().promptTemplate(),
                status,
                reviewerNote,
                existing.get().createdAt(),
                System.currentTimeMillis()
        );
        if (status == PersonalSkillStatus.APPROVED) {
            syncToSkillLibrary(updated);
        }
        return new PersonalSkillResult(true, "审核结果已更新", updated);
    }

    private String validateSkillPackage(String name, String description, String category, String promptTemplate) {
        if (name == null || name.isBlank()) {
            return "名称不能为空";
        }
        if (description == null || description.isBlank()) {
            return "描述不能为空";
        }
        if (category == null || category.isBlank()) {
            return "分类不能为空";
        }
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return "Prompt 模板不能为空";
        }
        if (!promptTemplate.contains("{{input}}") && !promptTemplate.contains("{{user_input}}")) {
            return "Prompt 模板需包含 {{input}} 占位符";
        }
        return null;
    }

    private void syncToSkillLibrary(PersonalSkill skill) {
        if (skillRepository == null) {
            return;
        }
        SkillAdmin adminSkill = new SkillAdmin(
                skill.id(),
                skill.name(),
                skill.description(),
                "default.png",
                skill.category(),
                true,
                "",
                List.of(),
                null,       // i18nJson
                "user",     // source
                "zh",       // sourceLang
                "basic",    // qualityTier
                SkillType.GENERAL,
                SkillKind.PROMPT_ONLY,
                skill.promptTemplate(),
                SkillExecutionMode.SINGLE,
                System.currentTimeMillis()
        );
        if (skillRepository.getSkill(skill.id()).isPresent()) {
            skillRepository.updateSkill(adminSkill);
        } else {
            skillRepository.saveSkill(adminSkill);
        }
        if (cacheService != null) {
            try {
                cacheService.invalidateSkillCache();
            } catch (Exception ignored) {
                log.warn("Cache invalidation failed during personal skill approval, continuing: {}", ignored.getMessage());
            }
        }
    }
}
