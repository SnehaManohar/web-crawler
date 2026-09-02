package com.crawler.model;

/**
 * Lifecycle state of a {@code CrawlJob}. This is the job-level state machine:
 *
 * <pre>
 *   QUEUED ---> RUNNING ---> COMPLETED
 *                      \---> FAILED
 * </pre>
 *
 * <ul>
 *   <li>QUEUED    - job persisted, seed pages recorded, nothing dispatched yet.</li>
 *   <li>RUNNING   - at least one seed page has been handed to the worker pool; more pages may
 *                   still be discovered as pages are crawled.</li>
 *   <li>COMPLETED - terminal. Every discovered page reached a terminal page state
 *                   (FETCHED / FAILED / SKIPPED) and at least one page was fetched.</li>
 *   <li>FAILED    - terminal. Every discovered page failed to fetch (e.g. all seed URLs
 *                   were unreachable).</li>
 * </ul>
 *
 * The transition into a terminal state is driven by an atomic "pending pages" counter per job
 * (see {@code JobProgressTracker}) hitting zero - never by a worker "deciding" it was the last.
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
