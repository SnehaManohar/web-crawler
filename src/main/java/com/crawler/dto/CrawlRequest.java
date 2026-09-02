package com.crawler.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body of {@code POST /crawl-jobs}.
 *
 * @param urls     one or more absolute http(s) URLs to start crawling from.
 * @param maxDepth optional override for how many link-levels to follow (seeds are depth 0). If
 *                 omitted, the server default ({@code crawler.max-depth}) is used.
 */
public record CrawlRequest(@NotEmpty List<String> urls, Integer maxDepth) {}
