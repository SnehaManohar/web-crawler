package com.crawler.crawl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * The in-memory hot-path state for every running job, keyed by job id in a
 * {@link ConcurrentHashMap}. Two concerns live here, both of which are accessed concurrently by
 * the whole worker pool and must be cheap and lock-free:
 *
 * <ol>
 *   <li><b>Deduplication</b> - {@link #markDiscovered} adds a normalized URL to the job's
 *       visited set and returns whether it was new. Backed by
 *       {@link ConcurrentHashMap#newKeySet()}, so "have we seen this URL, and if not claim it"
 *       is a single atomic step. This is what stops the same URL being crawled twice within a
 *       job, no matter how many pages link to it or how many workers hit it at once.</li>
 *   <li><b>Completion</b> - an {@link AtomicInteger} of pages still to be processed.
 *       {@link #onPageDiscovered} bumps it, {@link #onPageProcessed} drops it and reports
 *       whether it just hit zero. A job is done the instant that counter reaches zero, and
 *       exactly one worker sees that transition.</li>
 * </ol>
 *
 * <p>This is a cache of derivable state, not the source of truth: it can be rebuilt from the
 * {@code crawl_pages} table (see {@code JobReconciliationScheduler}), which is what makes a
 * process restart survivable.
 */
@Component
public class JobProgressTracker {

    private record JobState(Set<String> visited, AtomicInteger pending) {}

    private final ConcurrentHashMap<String, JobState> states = new ConcurrentHashMap<>();

    /** Called once when a job is accepted, before any seed task is dispatched. */
    public void startTracking(String jobId) {
        states.put(jobId, new JobState(ConcurrentHashMap.newKeySet(), new AtomicInteger(0)));
    }

    public boolean isTracking(String jobId) {
        return states.containsKey(jobId);
    }

    /**
     * Atomically records that {@code normalizedUrl} has been seen for this job.
     *
     * @return {@code true} if this is the first time - the caller should create a page and
     *     schedule it; {@code false} if it was already claimed - the caller must skip it.
     */
    public boolean markDiscovered(String jobId, String normalizedUrl) {
        JobState state = states.get(jobId);
        return state != null && state.visited().add(normalizedUrl);
    }

    /** Number of unique URLs discovered for this job so far - used to enforce the max-pages cap. */
    public int discoveredCount(String jobId) {
        JobState state = states.get(jobId);
        return state == null ? 0 : state.visited().size();
    }

    /** Called when a new page has been persisted and is about to be queued. */
    public void onPageDiscovered(String jobId) {
        JobState state = states.get(jobId);
        if (state != null) {
            state.pending().incrementAndGet();
        }
    }

    /**
     * Called exactly once per page when it reaches a terminal state.
     *
     * @return {@code true} if this call brought the outstanding-page count to zero, i.e. the
     *     job is now complete. Only one caller ever gets {@code true}.
     */
    public boolean onPageProcessed(String jobId) {
        JobState state = states.get(jobId);
        if (state == null) {
            return false;
        }
        return state.pending().decrementAndGet() == 0;
    }

    public void stopTracking(String jobId) {
        states.remove(jobId);
    }
}
