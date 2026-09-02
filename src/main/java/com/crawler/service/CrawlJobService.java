package com.crawler.service;

import com.crawler.config.CrawlerProperties;
import com.crawler.crawl.CrawlTask;
import com.crawler.crawl.CrawlTaskExecutor;
import com.crawler.crawl.JobProgressTracker;
import com.crawler.dto.CrawlRequest;
import com.crawler.entity.CrawlJob;
import com.crawler.entity.CrawlPage;
import com.crawler.model.JobStatus;
import com.crawler.model.PageStatus;
import com.crawler.model.UrlType;
import com.crawler.repository.CrawlJobRepository;
import com.crawler.repository.CrawlPageRepository;
import com.crawler.url.UrlClassifier;
import com.crawler.url.UrlNormalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Thin orchestrator for {@code POST /crawl-jobs}: validate -&gt; persist the job and its seed
 * pages -&gt; start tracking -&gt; queue the seed tasks. It does not fetch, parse, or decide
 * completion - those belong to the crawl pipeline.
 *
 * <p>Seed pages are persisted first and only queued once the transaction commits (via a
 * post-commit hook), so a worker never picks up a seed task before its {@code CrawlPage} row is
 * visible.
 */
@Service
public class CrawlJobService {

    private static final Logger log = LoggerFactory.getLogger(CrawlJobService.class);

    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;
    private final UrlNormalizer urlNormalizer;
    private final UrlClassifier urlClassifier;
    private final JobProgressTracker tracker;
    private final CrawlTaskExecutor taskExecutor;
    private final CrawlerProperties properties;

    public CrawlJobService(
            CrawlJobRepository jobRepository,
            CrawlPageRepository pageRepository,
            UrlNormalizer urlNormalizer,
            UrlClassifier urlClassifier,
            JobProgressTracker tracker,
            CrawlTaskExecutor taskExecutor,
            CrawlerProperties properties) {
        this.jobRepository = jobRepository;
        this.pageRepository = pageRepository;
        this.urlNormalizer = urlNormalizer;
        this.urlClassifier = urlClassifier;
        this.tracker = tracker;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
    }

    @Transactional
    public CrawlJob submit(CrawlRequest request) {
        if (request.urls() == null || request.urls().isEmpty()) {
            throw new IllegalArgumentException("At least one URL is required");
        }

        int maxDepth = request.maxDepth() != null ? request.maxDepth() : properties.getMaxDepth();
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0");
        }

        // Normalize + dedupe seeds, but keep the first raw form of each for display / storage.
        record Seed(String rawUrl, String normalizedUrl) {}
        List<Seed> seeds = new ArrayList<>();
        Set<String> seenNormalized = new LinkedHashSet<>();
        for (String raw : request.urls()) {
            String normalized = urlNormalizer.normalize(raw);
            if (normalized == null) {
                throw new IllegalArgumentException("Not an absolute http(s) URL: " + raw);
            }
            if (seenNormalized.add(normalized)) {
                seeds.add(new Seed(raw.trim(), normalized));
            }
        }

        Instant now = Instant.now();
        String jobId = UUID.randomUUID().toString();
        CrawlJob job =
                CrawlJob.builder()
                        .id(jobId)
                        .status(JobStatus.QUEUED)
                        .seedUrls(new LinkedHashSet<>(seeds.stream().map(Seed::rawUrl).toList()))
                        .maxDepth(maxDepth)
                        .maxPages(properties.getMaxPagesPerJob())
                        .createdAt(now)
                        .build();
        jobRepository.save(job);

        tracker.startTracking(jobId);

        List<CrawlTask> seedTasks = new ArrayList<>();
        for (Seed seed : seeds) {
            tracker.markDiscovered(jobId, seed.normalizedUrl());
            UrlType type = urlClassifier.classify(seed.normalizedUrl());
            CrawlPage page =
                    CrawlPage.builder()
                            .jobId(jobId)
                            .parentPageId(null)
                            .url(seed.rawUrl())
                            .normalizedUrl(seed.normalizedUrl())
                            .depth(0)
                            .urlType(type)
                            .status(PageStatus.PENDING)
                            .createdAt(now)
                            .build();
            pageRepository.save(page);
            tracker.onPageDiscovered(jobId);
            seedTasks.add(new CrawlTask(jobId, page.getId(), seed.rawUrl(), seed.normalizedUrl(), type, 0));
        }

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(now);
        jobRepository.save(job);

        runAfterCommit(() -> seedTasks.forEach(taskExecutor::submit));

        log.info("Accepted crawl job {} with {} seed URL(s), maxDepth={}", jobId, seeds.size(), maxDepth);
        return job;
    }

    @Transactional(readOnly = true)
    public CrawlJob getJob(String jobId) {
        return jobRepository
                .findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("No crawl job found for id " + jobId));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }
}
