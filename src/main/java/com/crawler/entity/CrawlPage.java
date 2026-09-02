package com.crawler.entity;

import com.crawler.model.PageStatus;
import com.crawler.model.UrlType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One URL within one job. Pages form a tree: {@link #parentPageId} is {@code null} for a seed
 * and points at the page whose HTML first linked to this URL otherwise. That tree is exactly
 * the nested structure {@code GET /crawl-jobs/{id}/result} returns.
 *
 * <p>{@link #normalizedUrl} is the deduplication key - a job never has two pages with the same
 * normalized URL (enforced by a unique constraint), so a URL linked from three different pages
 * is still crawled once, attached under whichever page reached it first.
 */
@Entity
@Table(
        name = "crawl_pages",
        indexes = {
            @Index(name = "idx_page_job_status", columnList = "job_id, status"),
            @Index(name = "idx_page_job_normalized_url", columnList = "job_id, normalized_url", unique = true)
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Optimistic lock - guards against two workers ever processing the same page row. */
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    /** {@code null} for a seed page; otherwise the {@code CrawlPage.id} that linked here first. */
    @Column(name = "parent_page_id")
    private Long parentPageId;

    /** The URL exactly as discovered (relative links already resolved against the parent). */
    @Column(name = "url", nullable = false, length = 2000)
    private String url;

    /** Canonical form used for deduplication - see {@code UrlNormalizer}. */
    @Column(name = "normalized_url", nullable = false, length = 2000)
    private String normalizedUrl;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Enumerated(EnumType.STRING)
    @Column(name = "url_type", nullable = false)
    private UrlType urlType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PageStatus status;

    /** Populated only when {@code status == FAILED}. */
    @Column(name = "error", length = 2000)
    private String error;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "crawl_page_images", joinColumns = @JoinColumn(name = "page_id"))
    @Column(name = "image_url", length = 2000)
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "fetched_at")
    private Instant fetchedAt;
}
