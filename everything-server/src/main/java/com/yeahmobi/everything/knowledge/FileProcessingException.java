package com.yeahmobi.everything.knowledge;

/**
 * Exception thrown when knowledge base file processing fails.
 * Covers text extraction failures, unsupported formats, and I/O errors.
 */
public class FileProcessingException extends Exception {

    public FileProcessingException(String message) {
        super(message);
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
