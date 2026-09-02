package com.crawler.url;

import static org.assertj.core.api.Assertions.assertThat;

import com.crawler.model.UrlType;
import org.junit.jupiter.api.Test;

class UrlClassifierTest {

    private final UrlClassifier classifier = new UrlClassifier();

    @Test
    void classifiesImageExtensionsAsImage() {
        assertThat(classifier.classify("https://cdn.example.com/a/logo.PNG")).isEqualTo(UrlType.IMAGE);
        assertThat(classifier.classify("https://example.com/photo.jpeg")).isEqualTo(UrlType.IMAGE);
        assertThat(classifier.classify("https://example.com/icon.svg")).isEqualTo(UrlType.IMAGE);
    }

    @Test
    void classifiesPlainPagesAsHtmlPage() {
        assertThat(classifier.classify("https://example.com/about")).isEqualTo(UrlType.HTML_PAGE);
        assertThat(classifier.classify("https://example.com/")).isEqualTo(UrlType.HTML_PAGE);
        assertThat(classifier.classify("https://example.com/posts/2026/01")).isEqualTo(UrlType.HTML_PAGE);
    }

    @Test
    void classifiesNonPageBinariesAndNonHttpAsUnsupported() {
        assertThat(classifier.classify("https://example.com/report.pdf")).isEqualTo(UrlType.UNSUPPORTED);
        assertThat(classifier.classify("https://example.com/archive.zip")).isEqualTo(UrlType.UNSUPPORTED);
        assertThat(classifier.classify("ftp://example.com/file")).isEqualTo(UrlType.UNSUPPORTED);
    }
}
