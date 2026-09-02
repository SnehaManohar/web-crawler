package com.crawler.repository;

import com.crawler.entity.CrawlJob;
import com.crawler.model.JobStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlJobRepository extends JpaRepository<CrawlJob, String> {

    List<CrawlJob> findByStatus(JobStatus status);
}
