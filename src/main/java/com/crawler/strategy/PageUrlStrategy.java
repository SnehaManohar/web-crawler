package com.crawler.strategy;

import com.crawler.fetch.FetchException;
import com.crawler.fetch.PageContentCache;
import com.crawler.fetch.ParsedPage;
import com.crawler.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles a normal HTML page: load it (through the shared {@link PageContentCache}, so a URL
 * seen elsewhere is not re-fetched), then hand back every image URL on the page and every
 * http(s) link as a child-URL candidate.
 *
 * <p>A fetch failure is not thrown - it is returned as a {@code FAILED} outcome carrying the
 * error message, so the page still appears in the result tree as a partial result rather than
 * silently vanishing.
 */
@Component
public class PageUrlStrategy implements UrlStrategy {

    private static final Logger log = LoggerFactory.getLogger(PageUrlStrategy.class);

    private final PageContentCache pageContentCache;

    public PageUrlStrategy(PageContentCache pageContentCache) {
        this.pageContentCache = pageContentCache;
    }

    @Override
    public UrlType handles() {
        return UrlType.HTML_PAGE;
    }

    @Override
    public CrawlOutcome process(CrawlUnit unit) {
        try {
            ParsedPage page = pageContentCache.load(unit.normalizedUrl());
            return CrawlOutcome.fetched(page.imageUrls(), page.linkUrls());
        } catch (FetchException e) {
            log.debug("Page {} failed to fetch: {}", unit.url(), e.getMessage());
            return CrawlOutcome.failed(e.getMessage());
        }
    }
}
