package com.crawler.model;

/**
 * Classification of a URL, decided by {@code UrlClassifier} before the URL is ever fetched.
 * This is the discriminator the Strategy pattern keys off: each {@code UrlStrategy} declares
 * which {@code UrlType} it handles, so new URL categories can be supported by adding a strategy
 * rather than editing a switch.
 *
 * <ul>
 *   <li>HTML_PAGE   - an http(s) URL assumed to be a crawlable page; fetched and parsed for
 *                     {@code <img>} and {@code <a>} tags.</li>
 *   <li>IMAGE       - an http(s) URL whose extension is a known image type; it is itself an
 *                     image result and has no child pages.</li>
 *   <li>UNSUPPORTED - anything else (non-http scheme, mailto:, .pdf/.zip/... ); recorded as
 *                     SKIPPED, never fetched.</li>
 * </ul>
 */
public enum UrlType {
    HTML_PAGE,
    IMAGE,
    UNSUPPORTED
}
