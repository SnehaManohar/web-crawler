package com.crawler.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Response for {@code GET /crawl-jobs/{jobId}/status}. Every count is derived on read from the
 * current {@code crawl_pages} rows, so it always matches the tree {@code /result} would return.
 *
 * @param discoveredPages       unique URLs found for this job so far.
 * @param processedPages        of those, how many have reached a terminal page state.
 * @param failedPages           of the processed pages, how many failed to fetch.
 * @param completionPercentage  {@code processedPages / discoveredPages * 100}, 0-100.
 */
public record JobStatusResponse(
        String jobId,
        String status,
        Set<String> seedUrls,
        int maxDepth,
        long discoveredPages,
        long processedPages,
        long failedPages,
        double completionPercentage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String error) {}
