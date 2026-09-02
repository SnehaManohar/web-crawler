package com.crawler.fetch;

/**
 * Thrown when a URL cannot be retrieved. {@link #isRetryable()} distinguishes a transient
 * problem (timeout, connection reset, HTTP 5xx, 429) - worth another attempt - from a permanent
 * one (HTTP 4xx, malformed URL, unknown host) that retrying will never fix.
 */
public class FetchException extends RuntimeException {

    private final boolean retryable;

    public FetchException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public FetchException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
