package com.crawler.fetch;

import com.crawler.config.CrawlerProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real {@link PageFetcher}: a JDK {@link HttpClient} GET with a timeout, a crawler
 * User-Agent, and redirect following. It classifies the outcome into a retryable or permanent
 * {@link FetchException} but performs no retries itself - that is {@code RetryingPageFetcher}'s
 * job (Decorator), keeping this class to exactly one responsibility: one HTTP round trip.
 *
 * <p>Not a component - {@code FetchConfig} composes it inside the {@code RetryingPageFetcher}
 * decorator so the rest of the app only ever sees one {@link PageFetcher} bean.
 */
public class HttpPageFetcher implements PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpPageFetcher.class);

    private final HttpClient httpClient;
    private final DomainRateLimiter rateLimiter;
    private final Duration timeout;
    private final String userAgent;
    private final long maxBodyBytes;

    public HttpPageFetcher(CrawlerProperties properties, DomainRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.timeout = Duration.ofMillis(properties.getFetch().getTimeoutMillis());
        this.userAgent = properties.getFetch().getUserAgent();
        this.maxBodyBytes = properties.getFetch().getMaxBodyBytes();
        this.httpClient =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(timeout)
                        .build();
    }

    @Override
    public FetchResult fetch(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new FetchException("Malformed URL: " + url, false, e);
        }

        rateLimiter.acquire(uri.getHost());

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,image/*;q=0.8,*/*;q=0.5")
                        .GET()
                        .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FetchException("I/O error fetching " + url + ": " + e.getMessage(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("Interrupted fetching " + url, true, e);
        }

        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            throw new FetchException("HTTP " + status + " fetching " + url, true);
        }
        if (status >= 400) {
            throw new FetchException("HTTP " + status + " fetching " + url, false);
        }

        String body = response.body() == null ? "" : response.body();
        if (body.length() > maxBodyBytes) {
            log.debug("Truncating oversized body for {} ({} bytes)", url, body.length());
            body = body.substring(0, (int) maxBodyBytes);
        }
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        return new FetchResult(status, contentType, body);
    }
}
