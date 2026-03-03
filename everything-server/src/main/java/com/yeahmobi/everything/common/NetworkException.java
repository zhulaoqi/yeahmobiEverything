package com.yeahmobi.everything.common;

/**
 * Custom exception for network-related errors.
 * Wraps underlying I/O or HTTP errors into a unified exception type.
 */
public class NetworkException extends Exception {

    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
