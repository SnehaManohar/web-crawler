package com.crawler.url;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Produces the canonical form of a URL used as the deduplication key everywhere in the system
 * (the per-job visited set, the {@code crawl_pages} unique constraint, and the global fetch
 * cache key).
 *
 * <p>Normalization rules, chosen so that URLs that obviously point at the same resource collapse
 * to one key without being so aggressive that distinct pages merge:
 *
 * <ul>
 *   <li>scheme and host lower-cased</li>
 *   <li>default ports removed ({@code :80} for http, {@code :443} for https)</li>
 *   <li>fragment ({@code #...}) dropped - it never changes what the server returns</li>
 *   <li>a trailing slash on the path removed (except the root "/")</li>
 *   <li>an empty path becomes "/"</li>
 *   <li>the query string is kept as-is - {@code ?page=2} is a different resource</li>
 * </ul>
 */
@Component
public class UrlNormalizer {

    /**
     * @return the normalized absolute URL, or {@code null} if the input cannot be parsed as one.
     */
    public String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (!uri.isAbsolute() || uri.getHost() == null) {
                return null;
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost().toLowerCase(Locale.ROOT);

            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port != -1) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (uri.getQuery() != null) {
                sb.append('?').append(uri.getQuery());
            }
            return sb.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
