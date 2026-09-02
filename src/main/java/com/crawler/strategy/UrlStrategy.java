package com.crawler.strategy;

import com.crawler.model.UrlType;

/**
 * Strategy for processing one URL, chosen polymorphically by its {@link UrlType}. This is the
 * extension point of the crawler: supporting a new kind of URL (a sitemap, a paginated API, a
 * video page) means adding one implementation, not editing a chain of {@code if} statements.
 *
 * <ul>
 *   <li>{@code PageUrlStrategy}        - HTML_PAGE: fetch, parse, return images + child links.</li>
 *   <li>{@code ImageUrlStrategy}       - IMAGE: the URL is itself the single image; no children.</li>
 *   <li>{@code UnsupportedUrlStrategy} - UNSUPPORTED: record as SKIPPED, never fetched.</li>
 * </ul>
 */
public interface UrlStrategy {

    /** The one {@link UrlType} this strategy is responsible for. */
    UrlType handles();

    CrawlOutcome process(CrawlUnit unit);
}
