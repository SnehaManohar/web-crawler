package com.crawler.crawl;

import com.crawler.entity.CrawlJob;
import com.crawler.entity.CrawlPage;
import com.crawler.model.JobStatus;
import com.crawler.model.PageStatus;
import com.crawler.repository.CrawlJobRepository;
import com.crawler.repository.CrawlPageRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns exactly one decision: when a job's last page is done, what terminal state does the job
 * land in, and who gets told. Keeping this out of {@code CrawlTaskProcessor} means the
 * "process one URL" path and the "close out a job" path change for different reasons (SRP).
 *
 * <p>Terminal-state rule:
 * <ul>
 *   <li>any page still {@code PENDING} -&gt; not done (called too early; ignored)</li>
 *   <li>at least one page {@code FETCHED} -&gt; {@code COMPLETED} (possibly with some FAILED
 *       pages carrying error markers - a partial result)</li>
 *   <li>zero {@code FETCHED} and every page {@code FAILED} -&gt; {@code FAILED}</li>
 *   <li>zero {@code FETCHED} but some pages only {@code SKIPPED} -&gt; {@code COMPLETED}
 *       (nothing failed; there was just nothing to crawl)</li>
 * </ul>
 *
 * <p>{@code @Transactional} plus the terminal-status guard makes this idempotent: whether it is
 * called by the worker that processed the final page or by the reconciliation sweep, the job is
 * closed out once.
 */
@Service
public class JobCompletionService {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionService.class);

    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;
    private final JobProgressTracker tracker;
    private final List<JobLifecycleListener> listeners;

    public JobCompletionService(
            CrawlJobRepository jobRepository,
            CrawlPageRepository pageRepository,
            JobProgressTracker tracker,
            List<JobLifecycleListener> listeners) {
        this.jobRepository = jobRepository;
        this.pageRepository = pageRepository;
        this.tracker = tracker;
        this.listeners = listeners;
    }

    @Transactional
    public void finalizeIfComplete(String jobId) {
        CrawlJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus().isTerminal()) {
            return;
        }

        List<CrawlPage> pages = pageRepository.findByJobId(jobId);
        long pending = pages.stream().filter(p -> p.getStatus() == PageStatus.PENDING).count();
        if (pending > 0) {
            return;
        }

        long fetched = pages.stream().filter(p -> p.getStatus() == PageStatus.FETCHED).count();
        long failed = pages.stream().filter(p -> p.getStatus() == PageStatus.FAILED).count();

        JobStatus from = job.getStatus();
        JobStatus to = (fetched == 0 && failed > 0) ? JobStatus.FAILED : JobStatus.COMPLETED;

        job.setStatus(to);
        job.setCompletedAt(Instant.now());
        if (to == JobStatus.FAILED) {
            job.setError(
                    "no page could be fetched; "
                            + pages.stream()
                                    .map(p -> p.getUrl() + " -> " + p.getError())
                                    .collect(Collectors.joining("; ")));
        }
        jobRepository.save(job);
        tracker.stopTracking(jobId);

        log.info("Job {} finished: {} -> {} ({} pages, {} fetched, {} failed)",
                jobId, from, to, pages.size(), fetched, failed);
        listeners.forEach(l -> notifySafely(l, jobId, from, to));
    }

    private void notifySafely(JobLifecycleListener listener, String jobId, JobStatus from, JobStatus to) {
        try {
            listener.onStateChange(jobId, from, to);
        } catch (Exception e) {
            log.warn("Listener {} threw on {} -> {} for job {}", listener.getClass().getSimpleName(), from, to, jobId, e);
        }
    }
}
