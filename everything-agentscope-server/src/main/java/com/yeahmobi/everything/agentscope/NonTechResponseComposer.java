package com.yeahmobi.everything.agentscope;

/**
 * Normalizes operation responses for non-technical users.
 */
public final class NonTechResponseComposer {

    private NonTechResponseComposer() {
    }

    public static String buildTemplate(String taskSummary, String confirmText, String resultText) {
        String task = blankAs(taskSummary, "我将帮你推进当前 HR 事务并给出下一步动作。");
        String confirm = blankAs(confirmText, "请确认，我将立即继续执行。");
        String result = blankAs(resultText, "执行完成后我会同步结果与下一步。");
        return "【我将帮你做什么】\n"
                + task
                + "\n\n【请确认】\n"
                + confirm
                + "\n\n【结果】\n"
                + result;
    }

    private static String blankAs(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}

