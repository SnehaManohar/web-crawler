package com.crawler.model;

/**
 * Lifecycle state of a single {@code CrawlPage} (one URL within one job).
 *
 * <pre>
 *   PENDING ---> FETCHED   (page retrieved, images + child links extracted)
 *           \--> FAILED    (fetch failed after retries - kept as a partial result with an error marker)
 *           \--> SKIPPED   (URL type is not crawlable, e.g. mailto: or a .zip)
 * </pre>
 *
 * FETCHED / FAILED / SKIPPED are all terminal; a job completes once none of its pages are
 * PENDING.
 */
public enum PageStatus {
    PENDING,
    FETCHED,
    FAILED,
    SKIPPED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
