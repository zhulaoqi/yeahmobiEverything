package com.yeahmobi.everything.workfollowup;

import java.util.List;

/**
 * Service interface for personal work follow-up todos.
 */
public interface WorkFollowupService {

    WorkTodo createTodo(String title, String dueAt, String priority, String note);

    List<WorkTodo> listTodos(String status, String sortBy);

    WorkTodo completeTodo(String id, String review);

    WorkTodo postponeTodo(String id, int hours);

    WorkTodo deleteTodo(String id);

    WorkTodo updateTodoNote(String id, String note);

    WorkTodoMeta getTodoMeta(String todoId);

    WorkTodoMeta upsertTodoMeta(WorkTodoMeta meta);

    java.util.Map<String, WorkTodoMeta> listTodoMeta();
}
