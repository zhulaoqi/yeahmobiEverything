package com.yeahmobi.everything.agentscope;

import java.util.Locale;

/**
 * Lightweight keyword router for HR operation intents.
 */
public final class HrIntentRouter {

    private HrIntentRouter() {
    }

    public enum HrIntentType {
        RECRUITMENT_ADVANCE,
        INTERVIEW_DECISION,
        OFFER_ONBOARDING,
        HR_TRANSACTION,
        GENERAL
    }

    public static HrIntentType detect(String input) {
        String text = input == null ? "" : input.toLowerCase(Locale.ROOT);
        if (text.contains("招聘") || text.contains("候选人") || text.contains("简历")
                || text.contains("jd") || text.contains("筛选")) {
            return HrIntentType.RECRUITMENT_ADVANCE;
        }
        if (text.contains("面试") || text.contains("评价") || text.contains("面评")
                || text.contains("能力项")) {
            return HrIntentType.INTERVIEW_DECISION;
        }
        if (text.contains("offer") || text.contains("入职") || text.contains("背调")
                || text.contains("薪资沟通")) {
            return HrIntentType.OFFER_ONBOARDING;
        }
        if (text.contains("转正") || text.contains("调岗") || text.contains("离职")
                || text.contains("人事流程")) {
            return HrIntentType.HR_TRANSACTION;
        }
        return HrIntentType.GENERAL;
    }
}

