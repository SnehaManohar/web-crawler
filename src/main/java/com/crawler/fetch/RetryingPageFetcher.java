package com.crawler.fetch;

import com.crawler.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator over another {@link PageFetcher} that adds bounded retry with exponential backoff
 * for <em>retryable</em> failures only. A permanent {@link FetchException} (HTTP 4xx, bad URL)
 * is rethrown on the first attempt without waiting.
 *
 * <p>Unlike the notification system's retry - which is async because provider backoff can be
 * minutes - a page fetch is a short synchronous operation, so blocking the worker for a
 * few hundred milliseconds between attempts is acceptable and keeps the crawl model simple.
 */
public class RetryingPageFetcher implements PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(RetryingPageFetcher.class);

    private final PageFetcher delegate;
    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final double multiplier;
    private final long maxBackoffMillis;

    public RetryingPageFetcher(PageFetcher delegate, CrawlerProperties properties) {
        this.delegate = delegate;
        CrawlerProperties.Retry retry = properties.getRetry();
        this.maxAttempts = Math.max(1, retry.getMaxAttempts());
        this.initialBackoffMillis = retry.getInitialBackoffMillis();
        this.multiplier = retry.getMultiplier();
        this.maxBackoffMillis = retry.getMaxBackoffMillis();
    }

    @Override
    public FetchResult fetch(String url) {
        FetchException last = null;
        long backoff = initialBackoffMillis;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.fetch(url);
            } catch (FetchException e) {
                last = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    throw e;
                }
                log.debug("Fetch attempt {}/{} for {} failed ({}), retrying in {}ms",
                        attempt, maxAttempts, url, e.getMessage(), backoff);
                sleep(backoff);
                backoff = Math.min((long) (backoff * multiplier), maxBackoffMillis);
            }
        }
        throw last; // unreachable - the loop always returns or throws
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted during retry backoff", true, e);
        }
    }
}
