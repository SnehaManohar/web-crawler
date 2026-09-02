package com.crawler.crawl;

import com.crawler.model.UrlType;

/**
 * One unit of work on the queue: "crawl this already-persisted {@code CrawlPage}". It carries
 * only identifiers and the pre-computed URL type - everything else is loaded from the database
 * by the worker, so a task surviving in the queue across a state change is still valid.
 */
public record CrawlTask(
        String jobId, long pageId, String url, String normalizedUrl, UrlType urlType, int depth) {}
