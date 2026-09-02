package com.crawler.service;

import com.crawler.dto.JobStatusResponse;
import com.crawler.entity.CrawlJob;
import com.crawler.model.PageStatus;
import com.crawler.repository.CrawlJobRepository;
import com.crawler.repository.CrawlPageRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the {@code GET /crawl-jobs/{jobId}/status} response. Progress numbers are counted from
 * the {@code crawl_pages} table on every call rather than stored on the job, so they can never
 * disagree with the result tree.
 */
@Service
public class JobStatusService {

    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;

    public JobStatusService(CrawlJobRepository jobRepository, CrawlPageRepository pageRepository) {
        this.jobRepository = jobRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional(readOnly = true)
    public JobStatusResponse getStatus(String jobId) {
        CrawlJob job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new NoSuchElementException("No crawl job found for id " + jobId));

        long discovered = pageRepository.countByJobId(jobId);
        long pending = pageRepository.countByJobIdAndStatus(jobId, PageStatus.PENDING);
        long failed = pageRepository.countByJobIdAndStatus(jobId, PageStatus.FAILED);
        long processed = discovered - pending;
        double percentage = discovered == 0 ? 0.0 : round1(processed * 100.0 / discovered);

        return new JobStatusResponse(
                job.getId(),
                job.getStatus().name(),
                job.getSeedUrls(),
                job.getMaxDepth(),
                discovered,
                processed,
                failed,
                percentage,
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getError());
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
