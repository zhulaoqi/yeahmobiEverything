package com.yeahmobi.everything.workfollowup;

/**
 * Structured notification metadata for one todo.
 */
public record WorkTodoMeta(
        String todoId,
        int leadMinutes,
        String channelsCsv,
        String emailStatus,
        String emailLastAt,
        String emailLastError,
        String feishuStatus,
        String feishuLastAt,
        String feishuLastError,
        String lastObservedStatus,
        String updatedAt
) {
}
