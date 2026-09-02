package com.crawler.strategy;

import com.crawler.model.UrlType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Handles a URL that is itself an image (e.g. a seed URL pointing straight at a {@code .png},
 * or an {@code <a href>} to a full-size image). There is nothing to fetch or parse: the URL is
 * recorded as the single image of its own page node, and it has no children.
 */
@Component
public class ImageUrlStrategy implements UrlStrategy {

    @Override
    public UrlType handles() {
        return UrlType.IMAGE;
    }

    @Override
    public CrawlOutcome process(CrawlUnit unit) {
        return CrawlOutcome.fetched(List.of(unit.url()), List.of());
    }
}
