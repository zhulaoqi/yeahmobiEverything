package com.yeahmobi.everything.knowledge;

/**
 * Represents a knowledge base file or manual entry.
 *
 * @param id            unique file identifier (UUID)
 * @param fileName      the original file name or manual entry title
 * @param fileType      the file extension (pdf, md, txt) or "manual"
 * @param fileSize      the file size in bytes (0 for manual entries)
 * @param sourceType    "upload" for file uploads, "manual" for manual input
 * @param extractedText the extracted text content
 * @param uploadedAt    creation timestamp as epoch milliseconds
 * @param updatedAt     last update timestamp as epoch milliseconds
 */
public record KnowledgeFile(
    String id,
    String fileName,
    String fileType,
    long fileSize,
    String sourceType,
    String extractedText,
    long uploadedAt,
    long updatedAt
) {}
