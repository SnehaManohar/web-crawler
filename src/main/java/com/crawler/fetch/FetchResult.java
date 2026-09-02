package com.crawler.fetch;

/**
 * The raw outcome of retrieving one URL. {@code contentType} is the response's declared type
 * (may be {@code null}); {@code body} is the decoded response text.
 */
public record FetchResult(int statusCode, String contentType, String body) {}
