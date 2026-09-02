package com.crawler.url;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();

    @Test
    void lowercasesSchemeAndHostAndDropsFragment() {
        assertThat(normalizer.normalize("HTTP://Example.COM/Path#section"))
                .isEqualTo("http://example.com/Path");
    }

    @Test
    void removesDefaultPortsAndTrailingSlash() {
        assertThat(normalizer.normalize("https://example.com:443/a/b/")).isEqualTo("https://example.com/a/b");
        assertThat(normalizer.normalize("http://example.com:80")).isEqualTo("http://example.com/");
    }

    @Test
    void keepsQueryStringButNormalizesEmptyPathToRoot() {
        assertThat(normalizer.normalize("https://example.com?page=2")).isEqualTo("https://example.com/?page=2");
    }

    @Test
    void rejectsRelativeOrNonAbsoluteUrls() {
        assertThat(normalizer.normalize("/relative/path")).isNull();
        assertThat(normalizer.normalize("not a url")).isNull();
        assertThat(normalizer.normalize("")).isNull();
        assertThat(normalizer.normalize(null)).isNull();
    }

    @Test
    void twoEquivalentUrlsNormalizeToTheSameKey() {
        String a = normalizer.normalize("https://Example.com/docs/");
        String b = normalizer.normalize("https://example.com:443/docs#intro");
        assertThat(a).isEqualTo(b);
    }
}
