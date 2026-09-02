package com.crawler.crawl;

import com.crawler.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** The one built-in {@link JobLifecycleListener}: writes every transition to the log. */
@Component
public class LoggingJobLifecycleListener implements JobLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingJobLifecycleListener.class);

    @Override
    public void onStateChange(String jobId, JobStatus from, JobStatus to) {
        log.info("Job {} transitioned {} -> {}", jobId, from, to);
    }
}
