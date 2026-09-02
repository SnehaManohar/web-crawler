package com.crawler.repository;

import com.crawler.entity.CrawlPage;
import com.crawler.model.PageStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlPageRepository extends JpaRepository<CrawlPage, Long> {

    List<CrawlPage> findByJobId(String jobId);

    List<CrawlPage> findByJobIdAndStatus(String jobId, PageStatus status);

    Optional<CrawlPage> findByJobIdAndNormalizedUrl(String jobId, String normalizedUrl);

    long countByJobId(String jobId);

    long countByJobIdAndStatus(String jobId, PageStatus status);
}
