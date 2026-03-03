package com.yeahmobi.everything.repository.mysql;

import java.util.List;

/**
 * Repository interface for Skill-KnowledgeFile binding persistence in MySQL.
 */
public interface SkillKnowledgeBindingRepository {

    void bind(String skillId, String fileId);

    void unbind(String skillId, String fileId);

    void unbindAllForFile(String fileId);

    List<String> getFileIdsForSkill(String skillId);

    List<String> getSkillIdsForFile(String fileId);
}
