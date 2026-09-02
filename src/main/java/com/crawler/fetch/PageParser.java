package com.crawler.fetch;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Turns raw HTML into a {@link ParsedPage}. jsoup does the heavy lifting: given the page's own
 * URL as the base, {@code abs:src} / {@code abs:href} resolve relative references
 * ({@code ../logo.png}, {@code /about}) to absolute URLs for us.
 *
 * <p>Images are collected from {@code <img src>}, {@code <img data-src>} (lazy-loading), and
 * the candidates in any {@code srcset}. Links are collected from {@code <a href>}, restricted
 * to http(s).
 */
@Component
public class PageParser {

    public ParsedPage parse(String baseUrl, String html) {
        Document doc = Jsoup.parse(html == null ? "" : html, baseUrl);

        Set<String> images = new LinkedHashSet<>();
        for (Element img : doc.select("img")) {
            addHttp(images, img.absUrl("src"));
            addHttp(images, img.absUrl("data-src"));
        }
        for (Element el : doc.select("[srcset]")) {
            for (String candidate : el.attr("srcset").split(",")) {
                String urlPart = candidate.trim().split("\\s+")[0];
                addHttp(images, resolve(baseUrl, urlPart));
            }
        }

        Set<String> links = new LinkedHashSet<>();
        for (Element a : doc.select("a[href]")) {
            addHttp(links, a.absUrl("href"));
        }

        return new ParsedPage(new ArrayList<>(images), new ArrayList<>(links));
    }

    private void addHttp(Set<String> target, String url) {
        if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            target.add(url);
        }
    }

    private String resolve(String baseUrl, String maybeRelative) {
        if (maybeRelative == null || maybeRelative.isBlank()) {
            return null;
        }
        try {
            return URI.create(baseUrl).resolve(maybeRelative).toString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
