package com.yeahmobi.everything.workfollowup;

/**
 * Domain model for one work follow-up todo item.
 */
public record WorkTodo(
        String id,
        String title,
        String dueAt,
        String priority,
        String note,
        String status,
        String createdAt,
        String completedAt,
        String review
) {
}
