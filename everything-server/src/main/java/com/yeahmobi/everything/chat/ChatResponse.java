package com.yeahmobi.everything.chat;

/**
 * Represents the response from the AI model for a chat request.
 *
 * @param content      the final response content (may be null if unsuccessful)
 * @param plan         optional plan steps (multi-agent)
 * @param execution    optional execution output (multi-agent)
 * @param success      whether the request was processed successfully
 * @param errorMessage an error description if the request failed (null on success)
 */
public record ChatResponse(
        String content,
        String plan,
        String execution,
        boolean success,
        String errorMessage
) {}
