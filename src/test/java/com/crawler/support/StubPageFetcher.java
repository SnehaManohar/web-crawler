package com.crawler.support;

import com.crawler.fetch.FetchException;
import com.crawler.fetch.FetchResult;
import com.crawler.fetch.PageFetcher;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link PageFetcher} for tests: a fixed map of normalized-URL -&gt; HTML. Unknown
 * URLs raise a permanent {@link FetchException} (like a 404); URLs registered as "flaky" fail
 * with a retryable error a set number of times before succeeding.
 */
public class StubPageFetcher implements PageFetcher {

    private final Map<String, String> pages = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> flaky = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> fetchCounts = new ConcurrentHashMap<>();

    public StubPageFetcher page(String normalizedUrl, String html) {
        pages.put(normalizedUrl, html);
        return this;
    }

    /** This URL fails with a retryable error {@code failures} times, then serves {@code html}. */
    public StubPageFetcher flakyPage(String normalizedUrl, int failures, String html) {
        pages.put(normalizedUrl, html);
        flaky.put(normalizedUrl, new AtomicInteger(failures));
        return this;
    }

    public int fetchCount(String normalizedUrl) {
        return fetchCounts.getOrDefault(normalizedUrl, new AtomicInteger()).get();
    }

    @Override
    public FetchResult fetch(String url) {
        fetchCounts.computeIfAbsent(url, u -> new AtomicInteger()).incrementAndGet();

        AtomicInteger remainingFailures = flaky.get(url);
        if (remainingFailures != null && remainingFailures.getAndDecrement() > 0) {
            throw new FetchException("simulated transient failure for " + url, true);
        }

        String html = pages.get(url);
        if (html == null) {
            throw new FetchException("HTTP 404 fetching " + url, false);
        }
        return new FetchResult(200, "text/html", html);
    }
}
