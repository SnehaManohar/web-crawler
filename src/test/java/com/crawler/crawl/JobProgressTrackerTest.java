package com.crawler.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JobProgressTrackerTest {

    private final JobProgressTracker tracker = new JobProgressTracker();

    @Test
    void markDiscoveredIsTrueOncePerUrl() {
        tracker.startTracking("j1");
        assertThat(tracker.markDiscovered("j1", "http://a/")).isTrue();
        assertThat(tracker.markDiscovered("j1", "http://a/")).isFalse();
        assertThat(tracker.discoveredCount("j1")).isEqualTo(1);
    }

    @Test
    void jobIsCompleteExactlyWhenPendingReachesZero() {
        tracker.startTracking("j1");
        tracker.onPageDiscovered("j1");
        tracker.onPageDiscovered("j1");

        assertThat(tracker.onPageProcessed("j1")).isFalse(); // 2 -> 1
        assertThat(tracker.onPageProcessed("j1")).isTrue(); // 1 -> 0
    }

    @Test
    void onlyOneThreadObservesCompletionUnderConcurrency() throws InterruptedException {
        tracker.startTracking("j1");
        int pages = 1000;
        IntStream.range(0, pages).forEach(i -> tracker.onPageDiscovered("j1"));

        AtomicInteger completions = new AtomicInteger();
        var threads =
                IntStream.range(0, pages)
                        .mapToObj(
                                i ->
                                        new Thread(
                                                () -> {
                                                    if (tracker.onPageProcessed("j1")) {
                                                        completions.incrementAndGet();
                                                    }
                                                }))
                        .toList();
        threads.forEach(Thread::start);
        for (Thread t : threads) {
            t.join();
        }

        assertThat(completions.get()).isEqualTo(1);
    }
}
