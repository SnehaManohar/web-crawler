package com.crawler.dto;

import java.util.Set;

/** Response for {@code POST /crawl-jobs} - {@code 202 Accepted}. */
public record CrawlJobCreatedResponse(String jobId, String status, Set<String> seedUrls, int maxDepth) {}
