package com.crawler.crawl;

import com.crawler.config.CrawlerProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The producer/consumer boundary of the crawler. Producers ({@code CrawlJobService} for seed
 * pages, {@code CrawlTaskProcessor} for discovered child pages) call {@link #submit}; a fixed
 * pool of worker threads pulls tasks off an in-memory {@link BlockingQueue} and hands each to
 * {@link CrawlTaskProcessor}.
 *
 * <p>The queue is FIFO, and every task carries its {@code depth}, so the crawl proceeds
 * breadth-first within the depth bound: a page's children are only enqueued after the page
 * itself is processed, and never past {@code maxDepth}. BFS is chosen over DFS because the
 * result the API returns is a breadth-oriented "page then its children then their children"
 * tree, and a depth bound on BFS gives a predictable, bounded working set; a DFS with a depth
 * bound would explore one branch to the limit before touching the others.
 *
 * <p>In a multi-instance deployment this class is the seam where a real broker (SQS/Kafka)
 * would replace the in-memory queue; the database is already the source of truth, so nothing
 * else would change.
 */
@Component
public class CrawlTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(CrawlTaskExecutor.class);

    private final BlockingQueue<CrawlTask> queue = new LinkedBlockingQueue<>();
    private final CrawlTaskProcessor processor;
    private final int workerCount;
    private ExecutorService workers;
    private volatile boolean running = true;

    public CrawlTaskExecutor(@Lazy CrawlTaskProcessor processor, CrawlerProperties properties) {
        this.processor = processor;
        this.workerCount = Math.max(1, properties.getWorkerCount());
    }

    public void submit(CrawlTask task) {
        queue.add(task);
    }

    public int queueDepth() {
        return queue.size();
    }

    @PostConstruct
    void start() {
        workers =
                Executors.newFixedThreadPool(
                        workerCount,
                        r -> {
                            Thread t = new Thread(r, "crawl-worker");
                            t.setDaemon(true);
                            return t;
                        });
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workerLoop);
        }
        log.info("Started crawl worker pool with {} workers", workerCount);
    }

    private void workerLoop() {
        while (running) {
            CrawlTask task = null;
            try {
                task = queue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processor.process(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A task must never take a worker down - CrawlTaskProcessor already converts
                // failures into FAILED pages, so reaching here means a bug. Log and move on.
                log.error("Unexpected error processing crawl task {}", task, e);
            }
        }
    }

    @PreDestroy
    void stop() {
        running = false;
        if (workers != null) {
            workers.shutdownNow();
        }
    }
}
