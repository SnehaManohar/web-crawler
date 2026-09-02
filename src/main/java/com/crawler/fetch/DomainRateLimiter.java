package com.crawler.fetch;

import com.crawler.config.CrawlerProperties;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Enforces a minimum interval between requests to the same host, so a job with 50 links into
 * one domain does not hammer it 8-at-a-time. One lock per host is kept in a
 * {@link ConcurrentHashMap}; hosts are independent, so a slow domain never blocks requests to
 * others.
 *
 * <p>This is intentionally a politeness throttle, not a full token-bucket quota system - it is
 * the seam where a per-domain quota (or robots.txt {@code Crawl-delay}) would plug in.
 */
@Component
public class DomainRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DomainRateLimiter.class);

    private final long minIntervalMillis;
    private final ConcurrentHashMap<String, Object> hostLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastRequestAt = new ConcurrentHashMap<>();

    public DomainRateLimiter(CrawlerProperties properties) {
        this.minIntervalMillis = properties.getRateLimit().getPerDomainMinIntervalMillis();
    }

    /** Blocks the calling worker until it is allowed to hit {@code host}. */
    public void acquire(String host) {
        if (minIntervalMillis <= 0 || host == null) {
            return;
        }
        Object lock = hostLocks.computeIfAbsent(host, h -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            Long last = lastRequestAt.get(host);
            if (last != null) {
                long wait = minIntervalMillis - (now - last);
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            lastRequestAt.put(host, System.currentTimeMillis());
        }
    }
}
