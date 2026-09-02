package com.crawler.crawl;

import com.crawler.entity.CrawlJob;
import com.crawler.entity.CrawlPage;
import com.crawler.model.JobStatus;
import com.crawler.model.PageStatus;
import com.crawler.repository.CrawlJobRepository;
import com.crawler.repository.CrawlPageRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovers jobs whose in-memory state was lost - almost always because the process restarted
 * while a crawl was running. The database is the source of truth; this sweep rebuilds
 * everything else from it.
 *
 * <p>For each {@code RUNNING} job:
 * <ul>
 *   <li>if it is no longer tracked in memory, rebuild the {@link JobProgressTracker} entry
 *       (visited set = every page's normalized URL, pending count = the PENDING pages) and
 *       re-queue a task for every page still {@code PENDING};</li>
 *   <li>if it has zero {@code PENDING} pages, the completion signal was missed - ask
 *       {@link JobCompletionService} to close it out (idempotent).</li>
 * </ul>
 */
@Component
public class JobReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobReconciliationScheduler.class);

    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;
    private final JobProgressTracker tracker;
    private final JobCompletionService completionService;
    private final CrawlTaskExecutor taskExecutor;

    public JobReconciliationScheduler(
            CrawlJobRepository jobRepository,
            CrawlPageRepository pageRepository,
            JobProgressTracker tracker,
            JobCompletionService completionService,
            CrawlTaskExecutor taskExecutor) {
        this.jobRepository = jobRepository;
        this.pageRepository = pageRepository;
        this.tracker = tracker;
        this.completionService = completionService;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelayString = "${crawler.reconciliation-interval-millis:2000}")
    @Transactional
    public void reconcile() {
        for (CrawlJob job : jobRepository.findByStatus(JobStatus.RUNNING)) {
            List<CrawlPage> pages = pageRepository.findByJobId(job.getId());
            List<CrawlPage> pending =
                    pages.stream().filter(p -> p.getStatus() == PageStatus.PENDING).toList();

            if (!tracker.isTracking(job.getId())) {
                log.info("Rebuilding lost tracking state for job {} ({} pending pages)", job.getId(), pending.size());
                tracker.startTracking(job.getId());
                pages.forEach(p -> tracker.markDiscovered(job.getId(), p.getNormalizedUrl()));
                pending.forEach(p -> tracker.onPageDiscovered(job.getId()));
                pending.forEach(
                        p ->
                                taskExecutor.submit(
                                        new CrawlTask(
                                                job.getId(), p.getId(), p.getUrl(), p.getNormalizedUrl(),
                                                p.getUrlType(), p.getDepth())));
            }

            if (pending.isEmpty()) {
                completionService.finalizeIfComplete(job.getId());
            }
        }
    }
}
