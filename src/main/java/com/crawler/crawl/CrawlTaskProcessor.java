package com.crawler.crawl;

import com.crawler.entity.CrawlJob;
import com.crawler.entity.CrawlPage;
import com.crawler.model.PageStatus;
import com.crawler.model.UrlType;
import com.crawler.repository.CrawlJobRepository;
import com.crawler.repository.CrawlPageRepository;
import com.crawler.strategy.CrawlOutcome;
import com.crawler.strategy.CrawlUnit;
import com.crawler.strategy.UrlStrategyResolver;
import com.crawler.url.UrlClassifier;
import com.crawler.url.UrlNormalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The consumer half of the pipeline - it has exactly one job: take one {@link CrawlTask},
 * process it, and account for it. It does <em>not</em> fetch, parse, classify, or decide
 * completion; each of those is delegated:
 *
 * <ul>
 *   <li><b>fetch + parse + images + links</b> -&gt; the {@code UrlStrategy} chosen by
 *       {@link UrlStrategyResolver}</li>
 *   <li><b>which URLs are worth queuing</b> -&gt; {@link UrlNormalizer} + {@link UrlClassifier}
 *       + the job's {@code maxDepth} / {@code maxPages}</li>
 *   <li><b>is the job finished</b> -&gt; {@link JobProgressTracker} (the atomic pending
 *       counter) and, when it hits zero, {@link JobCompletionService}</li>
 * </ul>
 *
 * <p>Every path through {@link #process} accounts for the page exactly once - success, handled
 * failure, or unexpected exception - so the "pending pages" counter always trends to zero and
 * the job always terminates. That is the loop-never-exits bug this design is built to avoid.
 */
@Component
public class CrawlTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(CrawlTaskProcessor.class);
    private static final int MAX_ERROR_LENGTH = 1900;

    private final CrawlPageRepository pageRepository;
    private final CrawlJobRepository jobRepository;
    private final UrlStrategyResolver strategyResolver;
    private final UrlNormalizer urlNormalizer;
    private final UrlClassifier urlClassifier;
    private final JobProgressTracker tracker;
    private final JobCompletionService completionService;
    private final CrawlTaskExecutor taskExecutor;

    public CrawlTaskProcessor(
            CrawlPageRepository pageRepository,
            CrawlJobRepository jobRepository,
            UrlStrategyResolver strategyResolver,
            UrlNormalizer urlNormalizer,
            UrlClassifier urlClassifier,
            JobProgressTracker tracker,
            JobCompletionService completionService,
            CrawlTaskExecutor taskExecutor) {
        this.pageRepository = pageRepository;
        this.jobRepository = jobRepository;
        this.strategyResolver = strategyResolver;
        this.urlNormalizer = urlNormalizer;
        this.urlClassifier = urlClassifier;
        this.tracker = tracker;
        this.completionService = completionService;
        this.taskExecutor = taskExecutor;
    }

    public void process(CrawlTask task) {
        CrawlPage page = pageRepository.findById(task.pageId()).orElse(null);
        if (page == null) {
            log.warn("Task for missing page {} (job {}); accounting it as done", task.pageId(), task.jobId());
            finishPage(task.jobId());
            return;
        }
        if (page.getStatus().isTerminal()) {
            // A duplicate task (e.g. from reconciliation). The call that made it terminal has
            // already accounted for it - do nothing here.
            log.debug("Page {} already {}; ignoring duplicate task", page.getId(), page.getStatus());
            return;
        }

        try {
            CrawlJob job =
                    jobRepository
                            .findById(task.jobId())
                            .orElseThrow(() -> new IllegalStateException("Job " + task.jobId() + " not found"));

            CrawlOutcome outcome =
                    strategyResolver
                            .resolve(task.urlType())
                            .process(
                                    new CrawlUnit(
                                            task.jobId(),
                                            task.pageId(),
                                            task.url(),
                                            task.normalizedUrl(),
                                            task.urlType(),
                                            task.depth()));

            page.setStatus(outcome.status());
            page.setImageUrls(new ArrayList<>(outcome.imageUrls()));
            page.setError(truncate(outcome.error()));
            page.setFetchedAt(Instant.now());
            pageRepository.save(page);

            if (outcome.status() == PageStatus.FETCHED && task.depth() < job.getMaxDepth()) {
                scheduleChildren(job, page, outcome.childUrls(), task.depth() + 1);
            }
        } catch (Exception e) {
            log.error("Unexpected error crawling {} (page {}, job {})", task.url(), task.pageId(), task.jobId(), e);
            page.setStatus(PageStatus.FAILED);
            page.setError(truncate("internal error: " + e.getMessage()));
            page.setFetchedAt(Instant.now());
            try {
                pageRepository.save(page);
            } catch (Exception saveError) {
                log.error("Could not even persist FAILED state for page {}", task.pageId(), saveError);
            }
        }
        finishPage(task.jobId());
    }

    private void scheduleChildren(CrawlJob job, CrawlPage parent, List<String> childUrls, int childDepth) {
        List<CrawlTask> toQueue = new ArrayList<>();
        for (String rawChildUrl : childUrls) {
            if (tracker.discoveredCount(job.getId()) >= job.getMaxPages()) {
                log.info("Job {} reached the {}-page cap; not discovering further URLs", job.getId(), job.getMaxPages());
                break;
            }
            String normalized = urlNormalizer.normalize(rawChildUrl);
            if (normalized == null) {
                continue;
            }
            if (!tracker.markDiscovered(job.getId(), normalized)) {
                continue; // already seen in this job - deduplicated
            }

            UrlType childType = urlClassifier.classify(normalized);
            CrawlPage child =
                    CrawlPage.builder()
                            .jobId(job.getId())
                            .parentPageId(parent.getId())
                            .url(rawChildUrl)
                            .normalizedUrl(normalized)
                            .depth(childDepth)
                            .urlType(childType)
                            .status(PageStatus.PENDING)
                            .createdAt(Instant.now())
                            .build();
            pageRepository.save(child);
            tracker.onPageDiscovered(job.getId());
            toQueue.add(new CrawlTask(job.getId(), child.getId(), rawChildUrl, normalized, childType, childDepth));
        }
        toQueue.forEach(taskExecutor::submit);
    }

    private void finishPage(String jobId) {
        if (tracker.onPageProcessed(jobId)) {
            completionService.finalizeIfComplete(jobId);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH) + "...";
    }
}
