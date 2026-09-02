package com.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.crawler.config.CrawlerProperties;
import com.crawler.fetch.PageFetcher;
import com.crawler.fetch.RetryingPageFetcher;
import com.crawler.support.StubPageFetcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrawlApiIntegrationTest {

    @TestConfiguration
    static class StubConfig {
        @Bean
        StubPageFetcher stubPageFetcher() {
            return new StubPageFetcher()
                    .page(
                            "http://site.test/",
                            """
                            <img src="/home.png">
                            <a href="/about">about</a>
                            <a href="/gallery">gallery</a>
                            <a href="http://ext.test/">external</a>
                            """)
                    .page(
                            "http://site.test/about",
                            """
                            <img src="/team.jpg">
                            <a href="/">home</a>
                            """)
                    .page(
                            "http://site.test/gallery",
                            """
                            <img src="/g1.png"><img src="/g2.png">
                            <a href="/about">about again</a>
                            <a href="/full/hero.png">full size</a>
                            """)
                    .page("http://ext.test/", "<img src=\"/ext.png\">")
                    .flakyPage("http://flaky.test/", 2, "<img src=\"/ok.png\">")
                    .page("http://cache.test/", "<img src=\"/c.png\"><a href=\"/child\">c</a>")
                    .page("http://cache.test/child", "<img src=\"/child.png\">");
        }

        @Bean
        @Primary
        PageFetcher stubbedPageFetcher(StubPageFetcher stub, CrawlerProperties properties) {
            // Mirror the production wiring: the retry decorator wraps the (stub) fetcher.
            return new RetryingPageFetcher(stub, properties);
        }
    }

    @Autowired TestRestTemplate rest;
    @Autowired StubPageFetcher stub;

    @Test
    void crawlsSeedAndChildPagesAndAssemblesNestedImageTree() {
        String jobId = submit(Map.of("urls", List.of("http://site.test/")));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(jobId).get("status")).isEqualTo("COMPLETED"));

        Map<String, Object> status = status(jobId);
        assertThat(((Number) status.get("failedPages")).intValue()).isZero();
        assertThat(((Number) status.get("discoveredPages")).intValue()).isEqualTo(5);
        assertThat(((Number) status.get("completionPercentage")).doubleValue()).isEqualTo(100.0);

        Map<String, Object> result = result(jobId);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) result.get("totalImageCount")).intValue()).isEqualTo(6);

        List<Map<String, Object>> roots = asList(result.get("pages"));
        assertThat(roots).hasSize(1);
        Map<String, Object> root = roots.get(0);
        assertThat(root.get("url")).isEqualTo("http://site.test/");
        assertThat(asStrings(root.get("imageUrls"))).containsExactly("http://site.test/home.png");

        List<Map<String, Object>> children = asList(root.get("children"));
        assertThat(children).extracting(c -> c.get("url"))
                .containsExactlyInAnyOrder(
                        "http://site.test/about", "http://site.test/gallery", "http://ext.test/");

        Map<String, Object> gallery =
                children.stream().filter(c -> c.get("url").equals("http://site.test/gallery")).findFirst().orElseThrow();
        // /about is linked from gallery too but was already crawled under the root - deduplicated.
        // /full/hero.png is an image link: crawled by the IMAGE strategy, no fetch, no children.
        List<Map<String, Object>> galleryChildren = asList(gallery.get("children"));
        assertThat(galleryChildren).extracting(c -> c.get("url")).containsExactly("http://site.test/full/hero.png");
        assertThat(galleryChildren.get(0).get("urlType")).isEqualTo("IMAGE");
        assertThat(asStrings(galleryChildren.get(0).get("imageUrls")))
                .containsExactly("http://site.test/full/hero.png");
    }

    @Test
    void deduplicatesUrlsLinkedFromMultiplePages() {
        String jobId = submit(Map.of("urls", List.of("http://site.test/")));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(jobId).get("status")).isEqualTo("COMPLETED"));

        // /about is reachable from both / and /gallery, but is fetched exactly once.
        assertThat(stub.fetchCount("http://site.test/about")).isEqualTo(1);
    }

    @Test
    void retriesTransientFetchFailuresThenSucceeds() {
        String jobId = submit(Map.of("urls", List.of("http://flaky.test/"), "maxDepth", 0));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(jobId).get("status")).isEqualTo("COMPLETED"));

        Map<String, Object> result = result(jobId);
        assertThat(((Number) result.get("totalImageCount")).intValue()).isEqualTo(1);
    }

    @Test
    void marksJobFailedWhenEverySeedIsUnreachable() {
        String jobId = submit(Map.of("urls", List.of("http://does-not-exist.test/"), "maxDepth", 0));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(jobId).get("status")).isEqualTo("FAILED"));

        List<Map<String, Object>> roots = asList(result(jobId).get("pages"));
        assertThat(roots.get(0).get("status")).isEqualTo("FAILED");
        assertThat((String) roots.get(0).get("error")).contains("404");
    }

    @Test
    void reusesTheGlobalCacheAcrossJobs() {
        String job1 = submit(Map.of("urls", List.of("http://cache.test/")));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(job1).get("status")).isEqualTo("COMPLETED"));
        String job2 = submit(Map.of("urls", List.of("http://cache.test/")));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(status(job2).get("status")).isEqualTo("COMPLETED"));

        // Two jobs crawled the same page; it was only ever fetched once.
        assertThat(stub.fetchCount("http://cache.test/")).isEqualTo(1);
    }

    @Test
    void rejectsEmptyUrlList() {
        ResponseEntity<Map> response =
                rest.postForEntity("/crawl-jobs", Map.of("urls", List.of()), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returns404ForUnknownJob() {
        ResponseEntity<Map> response = rest.getForEntity("/crawl-jobs/nope/status", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---------------------------------------------------------------

    private String submit(Map<String, Object> body) {
        ResponseEntity<Map> response = rest.postForEntity("/crawl-jobs", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return (String) response.getBody().get("jobId");
    }

    private Map<String, Object> status(String jobId) {
        return rest.exchange(
                        "/crawl-jobs/" + jobId + "/status",
                        org.springframework.http.HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {})
                .getBody();
    }

    private Map<String, Object> result(String jobId) {
        return rest.exchange(
                        "/crawl-jobs/" + jobId + "/result",
                        org.springframework.http.HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {})
                .getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object raw) {
        return (List<Map<String, Object>>) raw;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStrings(Object raw) {
        return (List<String>) raw;
    }
}
