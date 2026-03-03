package com.yeahmobi.everything.admin;

import com.yeahmobi.everything.feedback.Feedback;
import com.yeahmobi.everything.skill.SkillImportReport;

import java.util.List;

/**
 * Service interface for admin management operations.
 * <p>
 * Provides methods for Skill management (CRUD, status toggle),
 * command-line Skill integration, and feedback management.
 * All Skill mutation operations invalidate the Redis Skill cache.
 * </p>
 */
public interface AdminService {

    /**
     * Gets all Skills with administrative metadata.
     *
     * @return a list of all SkillAdmin records
     */
    List<SkillAdmin> getAllSkills();

    /**
     * Integrates a new Skill via a command-line style command string.
     *
     * @param command the command string (e.g., {@code --name "翻译" --desc "翻译助手" --category "翻译"})
     * @return the integration result
     */
    SkillIntegrationResult integrateSkill(String command);

    /**
     * Creates a general (PROMPT_ONLY) Skill from a template form.
     * Validates required fields (name, description, category).
     * Invalidates the Skill cache on success.
     *
     * @param template the skill template
     * @return the creation result
     */
    SkillIntegrationResult createSkillFromTemplate(SkillTemplate template);

    /**
     * Creates a knowledge-based (KNOWLEDGE_RAG) Skill from a wizard template.
     * Validates required fields, creates the Skill, binds knowledge files,
     * and optionally creates a manual knowledge entry.
     * Invalidates the Skill cache on success.
     *
     * @param template the knowledge skill template
     * @return the creation result
     */
    SkillIntegrationResult createKnowledgeSkill(KnowledgeSkillTemplate template);

    /**
     * Renders a prompt template by replacing {@code {{user_input}}} placeholders
     * with the given sample input.
     *
     * @param promptTemplate the prompt template string
     * @param sampleInput    the sample user input
     * @return the rendered prompt string
     */
    String renderPromptTemplate(String promptTemplate, String sampleInput);

    /**
     * Enables or disables a Skill by ID.
     * Invalidates the Skill cache after the status change.
     *
     * @param skillId the skill ID
     * @param enabled the target enabled state
     */
    void toggleSkillStatus(String skillId, boolean enabled);

    /**
     * Updates localized display metadata for a Skill (i18nJson + quality tier).
     * Invalidates the Skill cache after update.
     *
     * @param skillId     the skill ID
     * @param i18nJson    i18n JSON string (e.g. {"zh-CN":{...}}); may be null/blank to clear
     * @param qualityTier quality tier (basic/verified); may be null/blank to keep existing
     * @return update result
     */
    SkillIntegrationResult updateSkillDisplay(String skillId, String i18nJson, String qualityTier);

    /**
     * Gets all user feedbacks, ordered by time descending.
     *
     * @return a list of all feedbacks
     */
    List<Feedback> getAllFeedbacks();

    /**
     * Marks a feedback as processed and records the processing time.
     *
     * @param feedbackId the feedback ID to mark as processed
     */
    void markFeedbackProcessed(String feedbackId);

    /**
     * Promotes a user to admin by user ID or email.
     *
     * @param userIdOrEmail user ID or email
     * @return operation result message
     */
    String promoteUserToAdmin(String userIdOrEmail);

    /**
     * Imports Anthropic skills from a local repository path.
     *
     * @param repoPath local path to anthropics/skills
     * @return number of imported skills
     */
    int importAnthropicSkills(String repoPath);

    /**
     * Imports skills from a local repository path.
     * The repository should follow the Agent Skills format (SKILL.md).
     *
     * @param repoPath local path to a skills repository
     * @return number of imported skills
     */
    int importSkillsFromPath(String repoPath);

    /**
     * Imports skills and returns a detailed report for user-facing feedback.
     *
     * @param repoPath local path to a skills repository
     * @return detailed import report (scan/import/update/skip/fail stats)
     */
    SkillImportReport importSkillsFromPathDetailed(String repoPath);
}
