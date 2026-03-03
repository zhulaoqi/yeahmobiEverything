package com.yeahmobi.everything.skill;

import com.yeahmobi.everything.admin.SkillAdmin;
import com.yeahmobi.everything.common.Config;
import com.yeahmobi.everything.common.HttpClientUtil;
import com.yeahmobi.everything.repository.mysql.MySQLDatabaseManager;
import com.yeahmobi.everything.repository.mysql.SkillRepositoryImpl;

import java.time.Duration;
import java.util.List;

/**
 * Batch localize skills to zh-CN i18n metadata.
 */
public class SkillLocalizationBackfillRunner {

    public static void main(String[] args) throws Exception {
        Config config = Config.getInstance();
        MySQLDatabaseManager mysql = new MySQLDatabaseManager(config);
        mysql.initialize();

        SkillRepositoryImpl skillRepository = new SkillRepositoryImpl(mysql);
        // Batch localization can be heavy for long skill docs; use a safer upper timeout.
        HttpClientUtil http = new HttpClientUtil(Duration.ofMillis(Math.max(120_000, config.getLlmApiTimeout())));
        SkillLocalizationService localizer = new SkillLocalizationService(config, http);

        List<SkillAdmin> all = skillRepository.getAllSkills();
        int scanned = 0;
        int skipped = 0;
        int localized = 0;
        int failed = 0;
        for (SkillAdmin s : all) {
            scanned++;
            if (s == null) {
                skipped++;
                continue;
            }
            if (s.i18nJson() != null && !s.i18nJson().isBlank()) {
                skipped++;
                continue;
            }

            var payload = localizer.localizeToZhCn(s);
            if (payload.isEmpty()) {
                failed++;
                continue;
            }

            SkillLocalizationPayload p = payload.get();
            String localizedDescription = p.localizedOneLine();
            String localizedUsageGuide = p.localizedUsageGuide();
            SkillAdmin updated = new SkillAdmin(
                    s.id(),
                    s.name(),
                    (localizedDescription != null && !localizedDescription.isBlank())
                            ? localizedDescription
                            : s.description(),
                    s.icon(),
                    s.category(),
                    s.enabled(),
                    (localizedUsageGuide != null && !localizedUsageGuide.isBlank())
                            ? localizedUsageGuide
                            : s.usageGuide(),
                    (s.examples() == null || s.examples().isEmpty()) ? p.exampleInputs() : s.examples(),
                    p.i18nJson(),
                    s.source(),
                    s.sourceLang(),
                    s.qualityTier(),
                    s.toolIds(),
                    s.toolGroups(),
                    s.contextPolicy(),
                    s.type(),
                    s.kind(),
                    s.promptTemplate(),
                    s.executionMode(),
                    s.createdAt()
            );
            skillRepository.updateSkill(updated);
            localized++;
            System.out.println("Localized: " + s.id() + " -> " + s.name());
        }

        System.out.println("Skill localization done. scanned=" + scanned
                + ", localized=" + localized
                + ", skipped=" + skipped
                + ", failed=" + failed);
    }
}

