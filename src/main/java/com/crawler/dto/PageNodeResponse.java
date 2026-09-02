package com.crawler.dto;

import java.util.List;

/**
 * One node in the nested crawl result. Mirrors a {@code CrawlPage} and its position in the
 * tree: {@link #imageUrls} are the images found <em>on this page</em>, {@link #children} are the
 * pages this page linked to (each a full node in turn).
 */
public record PageNodeResponse(
        String url,
        int depth,
        String status,
        String urlType,
        String error,
        List<String> imageUrls,
        List<PageNodeResponse> children) {}
