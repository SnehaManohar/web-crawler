package com.crawler.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageParserTest {

    private final PageParser parser = new PageParser();

    @Test
    void extractsAndAbsolutizesImagesAndLinks() {
        String html =
                """
                <html><body>
                  <img src="/img/logo.png">
                  <img src="banner.jpg">
                  <img data-src="https://cdn.example.com/lazy.webp">
                  <a href="/about">About</a>
                  <a href="https://other.example.com/x">External</a>
                  <a href="mailto:hi@example.com">Mail</a>
                </body></html>
                """;

        ParsedPage page = parser.parse("https://example.com/home/index.html", html);

        assertThat(page.imageUrls())
                .containsExactlyInAnyOrder(
                        "https://example.com/img/logo.png",
                        "https://example.com/home/banner.jpg",
                        "https://cdn.example.com/lazy.webp");
        assertThat(page.linkUrls())
                .containsExactlyInAnyOrder("https://example.com/about", "https://other.example.com/x");
    }

    @Test
    void deduplicatesRepeatedUrls() {
        String html =
                "<a href='/a'>1</a><a href='/a'>2</a><img src='/p.png'><img src='/p.png'>";
        ParsedPage page = parser.parse("https://example.com/", html);
        assertThat(page.linkUrls()).containsExactly("https://example.com/a");
        assertThat(page.imageUrls()).containsExactly("https://example.com/p.png");
    }

    @Test
    void handlesEmptyHtml() {
        ParsedPage page = parser.parse("https://example.com/", "");
        assertThat(page.imageUrls()).isEmpty();
        assertThat(page.linkUrls()).isEmpty();
    }
}
