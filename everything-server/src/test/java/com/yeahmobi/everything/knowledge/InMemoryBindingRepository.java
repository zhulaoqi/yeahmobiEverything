package com.yeahmobi.everything.knowledge;

import com.yeahmobi.everything.repository.mysql.SkillKnowledgeBindingRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of SkillKnowledgeBindingRepository for testing.
 */
class InMemoryBindingRepository implements SkillKnowledgeBindingRepository {

    // key = "skillId:fileId"
    private final Set<String> bindings = ConcurrentHashMap.newKeySet();

    @Override
    public void bind(String skillId, String fileId) {
        bindings.add(skillId + ":" + fileId);
    }

    @Override
    public void unbind(String skillId, String fileId) {
        bindings.remove(skillId + ":" + fileId);
    }

    @Override
    public void unbindAllForFile(String fileId) {
        bindings.removeIf(b -> b.endsWith(":" + fileId));
    }

    @Override
    public List<String> getFileIdsForSkill(String skillId) {
        return bindings.stream()
                .filter(b -> b.startsWith(skillId + ":"))
                .map(b -> b.substring(b.indexOf(':') + 1))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getSkillIdsForFile(String fileId) {
        return bindings.stream()
                .filter(b -> b.endsWith(":" + fileId))
                .map(b -> b.substring(0, b.indexOf(':')))
                .collect(Collectors.toList());
    }
}
