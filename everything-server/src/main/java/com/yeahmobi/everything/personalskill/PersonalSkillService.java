package com.yeahmobi.everything.personalskill;

import java.util.List;

/**
 * Service interface for personal skill management.
 */
public interface PersonalSkillService {

    PersonalSkillResult saveDraft(String userId, String name, String description,
                                  String category, String promptTemplate, String existingId);

    PersonalSkillResult submitForReview(String userId, String skillId);

    List<PersonalSkill> listByUser(String userId);

    List<PersonalSkill> listPending();

    PersonalSkillResult reviewSkill(String skillId, PersonalSkillStatus status, String reviewerNote);
}
