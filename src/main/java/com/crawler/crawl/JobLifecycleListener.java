package com.crawler.crawl;

import com.crawler.model.JobStatus;

/**
 * Observer of job state-machine transitions. Implementations are notified after the transition
 * is persisted. This is the seam for "tell the caller the job is done" - a webhook callback, a
 * metric, an email - without the completion logic needing to know any of those exist.
 */
public interface JobLifecycleListener {

    void onStateChange(String jobId, JobStatus from, JobStatus to);
}
