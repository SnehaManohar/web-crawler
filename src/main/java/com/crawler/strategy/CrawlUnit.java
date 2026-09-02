package com.crawler.strategy;

import com.crawler.model.UrlType;

/**
 * The immutable input to a {@link UrlStrategy}: one URL to process, in the context of one job,
 * at a known depth. A strategy gets exactly this and nothing else - it has no access to the
 * queue, the database, or the job - which keeps each strategy a pure function of its input and
 * trivially unit-testable.
 */
public record CrawlUnit(
        String jobId, long pageId, String url, String normalizedUrl, UrlType urlType, int depth) {}
