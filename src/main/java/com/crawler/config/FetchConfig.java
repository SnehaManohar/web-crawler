package com.crawler.config;

import com.crawler.fetch.DomainRateLimiter;
import com.crawler.fetch.HttpPageFetcher;
import com.crawler.fetch.PageFetcher;
import com.crawler.fetch.RetryingPageFetcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes the fetch pipeline: {@code RetryingPageFetcher( HttpPageFetcher )}. Only the
 * outermost {@link PageFetcher} is exposed as a bean, so the rest of the app depends on the
 * interface and the test suite can swap in an in-memory stub with one {@code @Primary} bean.
 */
@Configuration
public class FetchConfig {

    @Bean
    public PageFetcher pageFetcher(CrawlerProperties properties, DomainRateLimiter rateLimiter) {
        HttpPageFetcher http = new HttpPageFetcher(properties, rateLimiter);
        return new RetryingPageFetcher(http, properties);
    }
}
