package com.crawler.entity;

import com.crawler.model.JobStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One crawl request. A job fans out into many {@code CrawlPage} rows - one per unique URL
 * discovered while crawling, starting from {@link #seedUrls} at depth 0 and following child
 * links up to {@link #maxDepth}.
 *
 * <p>The job does not store progress counters (discovered / processed / failed). Those are
 * always derived from the current {@code CrawlPage} rows so they can never drift out of sync
 * with the actual crawl tree - see {@code JobProgressService}.
 */
@Entity
@Table(name = "crawl_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crawl_job_seed_urls", joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "seed_url", length = 2000)
    private Set<String> seedUrls = new HashSet<>();

    /** Maximum link depth to follow. Seeds are depth 0; a seed's links are depth 1; etc. */
    @Column(name = "max_depth", nullable = false)
    private int maxDepth;

    /** Safety cap on the number of unique pages crawled for this job. */
    @Column(name = "max_pages", nullable = false)
    private int maxPages;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Populated only when {@code status == FAILED}. */
    @Column(name = "error", length = 2000)
    private String error;
}
