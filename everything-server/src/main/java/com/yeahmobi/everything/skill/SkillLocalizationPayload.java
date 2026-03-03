package com.yeahmobi.everything.skill;

import java.util.List;

/**
 * Payload produced by {@link SkillLocalizationService}.
 *
 * @param i18nJson            the i18n JSON string to store in MySQL (e.g. {"zh-CN":{...}})
 * @param exampleInputs       suggested example inputs extracted from i18nJson (optional)
 * @param localizedOneLine    localized one-line description for list cards (optional)
 * @param localizedUsageGuide localized usage guide text for chat usage hints (optional)
 */
public record SkillLocalizationPayload(
        String i18nJson,
        List<String> exampleInputs,
        String localizedOneLine,
        String localizedUsageGuide
) {}

