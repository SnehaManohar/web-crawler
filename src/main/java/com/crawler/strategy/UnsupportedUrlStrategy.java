package com.crawler.strategy;

import com.crawler.model.UrlType;
import org.springframework.stereotype.Component;

/**
 * Handles anything that is not a crawlable page or an image - a {@code mailto:} link, a
 * {@code .pdf}, a {@code .zip}, an {@code ftp://} URL. The page node is recorded as
 * {@code SKIPPED} so the result tree still shows the link was seen, but nothing is fetched.
 */
@Component
public class UnsupportedUrlStrategy implements UrlStrategy {

    @Override
    public UrlType handles() {
        return UrlType.UNSUPPORTED;
    }

    @Override
    public CrawlOutcome process(CrawlUnit unit) {
        return CrawlOutcome.skipped("URL type not crawlable");
    }
}
