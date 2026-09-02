package com.crawler.dto;

import java.util.List;

/**
 * Response for {@code GET /crawl-jobs/{jobId}/result} - the full nested tree of pages and the
 * image URLs found on each. {@link #pages} has one entry per seed URL; each entry's
 * {@code children} recurse to the configured depth.
 *
 * @param totalImageCount all image URLs across every page (duplicates across pages counted once
 *                        per page they appear on).
 */
public record CrawlResultResponse(
        String jobId, String status, int totalPageCount, int totalImageCount, List<PageNodeResponse> pages) {}
