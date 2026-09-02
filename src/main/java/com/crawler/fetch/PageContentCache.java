package com.crawler.fetch;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Process-wide cache of parsed pages, keyed by <em>normalized</em> URL. The same URL routinely
 * shows up more than once - linked from several pages in one job, or requested by two jobs
 * running at the same time - and re-fetching it each time is wasted network and latency. This
 * map is the "URL map" that makes the crawler scale with the number of jobs rather than the
 * number of (job, URL) pairs.
 *
 * <p>Thread-safety: {@link ConcurrentHashMap#computeIfAbsent} makes "fetch-and-store" atomic per
 * key, so concurrent workers asking for the same new URL collapse onto a single fetch. Failed
 * fetches are never stored (the mapping function propagates the exception), so a transient
 * error does not poison the entry.
 *
 * <p>This is a runtime performance cache only - it is deliberately not persisted. The
 * authoritative record of what was crawled is the {@code crawl_pages} table.
 */
@Component
public class PageContentCache {

    private static final Logger log = LoggerFactory.getLogger(PageContentCache.class);

    private final PageFetcher pageFetcher;
    private final PageParser pageParser;

    private final ConcurrentHashMap<String, ParsedPage> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

    public PageContentCache(PageFetcher pageFetcher, PageParser pageParser) {
        this.pageFetcher = pageFetcher;
        this.pageParser = pageParser;
    }

    /**
     * @param normalizedUrl canonical URL (already run through {@code UrlNormalizer}).
     * @throws FetchException if the page cannot be fetched.
     */
    public ParsedPage load(String normalizedUrl) {
        ParsedPage cached = cache.get(normalizedUrl);
        if (cached != null) {
            hits.incrementAndGet();
            return cached;
        }
        return cache.computeIfAbsent(
                normalizedUrl,
                url -> {
                    misses.incrementAndGet();
                    FetchResult result = pageFetcher.fetch(url);
                    return pageParser.parse(url, result.body());
                });
    }

    public long hitCount() {
        return hits.get();
    }

    public long missCount() {
        return misses.get();
    }

    public int size() {
        return cache.size();
    }
}
