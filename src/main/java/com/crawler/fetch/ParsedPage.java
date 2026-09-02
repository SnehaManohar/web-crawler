package com.crawler.fetch;

import java.util.List;

/**
 * The result of fetching and parsing one HTML page: every absolute image URL found in it, and
 * every absolute http(s) link found in it. Both lists are de-duplicated and in document order.
 * Immutable - this is what the global {@code PageContentCache} stores and hands out.
 */
public record ParsedPage(List<String> imageUrls, List<String> linkUrls) {

    public ParsedPage {
        imageUrls = List.copyOf(imageUrls);
        linkUrls = List.copyOf(linkUrls);
    }
}
