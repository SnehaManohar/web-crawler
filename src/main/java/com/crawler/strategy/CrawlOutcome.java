package com.crawler.strategy;

import com.crawler.model.PageStatus;
import java.util.List;

/**
 * The immutable output of a {@link UrlStrategy}: what became of the page, the image URLs found
 * on it, and the child links to consider crawling next. The caller ({@code CrawlTaskProcessor})
 * is the only thing that acts on this - persisting the page, recording images, and feeding
 * {@link #childUrls()} back to the scheduler with {@code depth + 1}. The strategy itself never
 * touches shared state.
 */
public record CrawlOutcome(
        PageStatus status, List<String> imageUrls, List<String> childUrls, String error) {

    public static CrawlOutcome fetched(List<String> imageUrls, List<String> childUrls) {
        return new CrawlOutcome(PageStatus.FETCHED, List.copyOf(imageUrls), List.copyOf(childUrls), null);
    }

    public static CrawlOutcome failed(String error) {
        return new CrawlOutcome(PageStatus.FAILED, List.of(), List.of(), error);
    }

    public static CrawlOutcome skipped(String reason) {
        return new CrawlOutcome(PageStatus.SKIPPED, List.of(), List.of(), reason);
    }
}
