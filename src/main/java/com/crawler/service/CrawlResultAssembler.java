package com.crawler.service;

import com.crawler.dto.CrawlResultResponse;
import com.crawler.dto.PageNodeResponse;
import com.crawler.entity.CrawlJob;
import com.crawler.entity.CrawlPage;
import com.crawler.repository.CrawlJobRepository;
import com.crawler.repository.CrawlPageRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rebuilds the nested crawl result from the flat {@code crawl_pages} rows. Every page carries
 * its {@code parentPageId}, so the tree is just "group children by parent, then walk from the
 * roots (parentPageId == null)". Because the crawl already deduplicated URLs per job, each page
 * appears exactly once and the structure is a genuine tree - the {@code visited} guard here is
 * only belt-and-braces against a malformed row set.
 */
@Service
public class CrawlResultAssembler {

    private final CrawlJobRepository jobRepository;
    private final CrawlPageRepository pageRepository;

    public CrawlResultAssembler(CrawlJobRepository jobRepository, CrawlPageRepository pageRepository) {
        this.jobRepository = jobRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional(readOnly = true)
    public CrawlResultResponse assemble(String jobId) {
        CrawlJob job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new NoSuchElementException("No crawl job found for id " + jobId));

        List<CrawlPage> pages = pageRepository.findByJobId(jobId);

        Map<Long, List<CrawlPage>> childrenByParent = new HashMap<>();
        List<CrawlPage> roots = new ArrayList<>();
        for (CrawlPage page : pages) {
            if (page.getParentPageId() == null) {
                roots.add(page);
            } else {
                childrenByParent.computeIfAbsent(page.getParentPageId(), k -> new ArrayList<>()).add(page);
            }
        }
        roots.sort((a, b) -> Long.compare(a.getId(), b.getId()));

        Set<Long> visited = new HashSet<>();
        List<PageNodeResponse> rootNodes = new ArrayList<>();
        int imageCount = 0;
        for (CrawlPage root : roots) {
            PageNodeResponse node = toNode(root, childrenByParent, visited);
            rootNodes.add(node);
            imageCount += countImages(node);
        }

        return new CrawlResultResponse(
                jobId, job.getStatus().name(), pages.size(), imageCount, rootNodes);
    }

    private PageNodeResponse toNode(
            CrawlPage page, Map<Long, List<CrawlPage>> childrenByParent, Set<Long> visited) {
        List<PageNodeResponse> childNodes = new ArrayList<>();
        if (visited.add(page.getId())) {
            List<CrawlPage> children =
                    new ArrayList<>(childrenByParent.getOrDefault(page.getId(), List.of()));
            children.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            for (CrawlPage child : children) {
                childNodes.add(toNode(child, childrenByParent, visited));
            }
        }
        return new PageNodeResponse(
                page.getUrl(),
                page.getDepth(),
                page.getStatus().name(),
                page.getUrlType().name(),
                page.getError(),
                List.copyOf(page.getImageUrls()),
                childNodes);
    }

    private int countImages(PageNodeResponse node) {
        int total = node.imageUrls().size();
        for (PageNodeResponse child : node.children()) {
            total += countImages(child);
        }
        return total;
    }
}
