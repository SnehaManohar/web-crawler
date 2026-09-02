package com.crawler.controller;

import com.crawler.dto.CrawlJobCreatedResponse;
import com.crawler.dto.CrawlRequest;
import com.crawler.dto.CrawlResultResponse;
import com.crawler.dto.JobStatusResponse;
import com.crawler.entity.CrawlJob;
import com.crawler.service.CrawlJobService;
import com.crawler.service.CrawlResultAssembler;
import com.crawler.service.JobStatusService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three-endpoint crawler API.
 *
 * <ul>
 *   <li>{@code POST /crawl-jobs}                    - accept URLs, return a job id (202).</li>
 *   <li>{@code GET  /crawl-jobs/{jobId}/status}     - current processing status + progress.</li>
 *   <li>{@code GET  /crawl-jobs/{jobId}/result}     - nested tree of pages and their image URLs.</li>
 * </ul>
 *
 * All three are asynchronous-friendly: the POST returns immediately, and the two GETs are safe
 * to poll while the crawl is still running (status shows a completion percentage; result shows
 * the tree built so far).
 */
@RestController
@RequestMapping("/crawl-jobs")
public class CrawlController {

    private final CrawlJobService crawlJobService;
    private final JobStatusService jobStatusService;
    private final CrawlResultAssembler resultAssembler;

    public CrawlController(
            CrawlJobService crawlJobService,
            JobStatusService jobStatusService,
            CrawlResultAssembler resultAssembler) {
        this.crawlJobService = crawlJobService;
        this.jobStatusService = jobStatusService;
        this.resultAssembler = resultAssembler;
    }

    @PostMapping
    public ResponseEntity<CrawlJobCreatedResponse> create(@Valid @RequestBody CrawlRequest request) {
        CrawlJob job = crawlJobService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                        new CrawlJobCreatedResponse(
                                job.getId(), job.getStatus().name(), job.getSeedUrls(), job.getMaxDepth()));
    }

    @GetMapping("/{jobId}/status")
    public JobStatusResponse status(@PathVariable String jobId) {
        return jobStatusService.getStatus(jobId);
    }

    @GetMapping("/{jobId}/result")
    public CrawlResultResponse result(@PathVariable String jobId) {
        return resultAssembler.assemble(jobId);
    }
}
