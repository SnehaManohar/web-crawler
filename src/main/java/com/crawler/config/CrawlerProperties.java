package com.crawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All crawler tunables - see {@code application.yml} for the defaults and what each one does. */
@ConfigurationProperties(prefix = "crawler")
@Getter
@Setter
public class CrawlerProperties {

    /** Size of the fixed worker pool that consumes the crawl-task queue. */
    private int workerCount = 8;

    /** Default max link depth when a request does not specify one. Seeds are depth 0. */
    private int maxDepth = 2;

    /** Hard cap on unique pages crawled per job, regardless of depth. */
    private int maxPagesPerJob = 200;

    /** How often the reconciliation sweep runs (recovers jobs orphaned by a restart). */
    private long reconciliationIntervalMillis = 2000;

    private final Fetch fetch = new Fetch();
    private final Retry retry = new Retry();
    private final RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Fetch {
        private long timeoutMillis = 10_000;
        private String userAgent = "SimpleWebCrawler/0.1 (+https://example.invalid/bot)";
        /** Bodies larger than this (bytes) are not parsed - guards against huge downloads. */
        private long maxBodyBytes = 5_000_000;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 3;
        private long initialBackoffMillis = 200;
        private double multiplier = 2.0;
        private long maxBackoffMillis = 2000;
    }

    @Getter
    @Setter
    public static class RateLimit {
        /** Minimum gap between two requests to the same host, enforced per host. */
        private long perDomainMinIntervalMillis = 200;
    }
}
