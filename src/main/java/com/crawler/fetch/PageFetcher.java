package com.crawler.fetch;

/**
 * Retrieves the content at one URL. This is the single seam between the crawler and the outside
 * world - swapping the real HTTP implementation for an in-memory stub (as the test suite does)
 * needs no other change.
 */
public interface PageFetcher {

    /**
     * @throws FetchException if the URL cannot be retrieved.
     */
    FetchResult fetch(String url);
}
