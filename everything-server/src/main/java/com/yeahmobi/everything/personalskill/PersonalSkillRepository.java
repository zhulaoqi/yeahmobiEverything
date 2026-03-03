package com.yeahmobi.everything.personalskill;

import java.util.List;
import java.util.Optional;

/**
 * Repository for user personal skills.
 */
public interface PersonalSkillRepository {
    void save(PersonalSkill skill);

    void update(PersonalSkill skill);

    Optional<PersonalSkill> getById(String id);

    List<PersonalSkill> getByUser(String userId);

    List<PersonalSkill> getPending();

    void updateStatus(String id, PersonalSkillStatus status, String reviewerNote);
}
