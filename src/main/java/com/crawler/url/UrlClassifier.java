package com.crawler.url;

import com.crawler.model.UrlType;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides a URL's {@link UrlType} from its scheme and file extension alone, before any network
 * call. That type is what {@code UrlStrategyResolver} uses to pick the strategy that handles the
 * URL, so classification is the single place where "what kind of thing is this URL" is decided.
 *
 * <p>Extension-based classification is a deliberate simplification: a page served at
 * {@code /photo} with {@code Content-Type: image/png} is classified HTML_PAGE here. A production
 * crawler would refine the type from the response's {@code Content-Type} after fetching; that
 * would be a second classification pass, not a change to this one.
 */
@Component
public class UrlClassifier {

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "tiff", "avif");

    private static final Set<String> NON_PAGE_EXTENSIONS =
            Set.of(
                    "pdf", "zip", "gz", "tar", "rar", "7z", "mp3", "mp4", "avi", "mov", "wav", "doc",
                    "docx", "xls", "xlsx", "ppt", "pptx", "css", "js", "json", "xml", "rss", "woff",
                    "woff2", "ttf", "eot");

    public UrlType classify(String normalizedUrl) {
        if (normalizedUrl == null) {
            return UrlType.UNSUPPORTED;
        }
        URI uri = URI.create(normalizedUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return UrlType.UNSUPPORTED;
        }

        String extension = extensionOf(uri.getPath());
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return UrlType.IMAGE;
        }
        if (NON_PAGE_EXTENSIONS.contains(extension)) {
            return UrlType.UNSUPPORTED;
        }
        return UrlType.HTML_PAGE;
    }

    private String extensionOf(String path) {
        if (path == null) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int dot = lastSegment.lastIndexOf('.');
        return dot >= 0 ? lastSegment.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
