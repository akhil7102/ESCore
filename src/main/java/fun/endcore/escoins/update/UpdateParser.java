package fun.endcore.escoins.update;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust HTML parser for the BuiltByBit resource updates page.
 */
public final class UpdateParser {
    public static final String DEFAULT_UPDATES_URL = "https://builtbybit.com/resources/core-smp.125982/updates";

    private static final Pattern UPDATE_ID_QUERY_PATTERN = Pattern.compile("[?&]update=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_ID_PATH_PATTERN = Pattern.compile("/updates/(\\d+)", Pattern.CASE_INSENSITIVE);

    private UpdateParser() {}

    /**
     * Parses the HTML body of a BuiltByBit updates page and extracts the latest update.
     *
     * @param html the raw HTML content
     * @param baseUri the base URI for resolving relative links (defaults to DEFAULT_UPDATES_URL if null)
     * @return Optional containing UpdateInfo if successfully parsed, or empty if unable to detect
     */
    public static Optional<UpdateInfo> parse(String html, String baseUri) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }

        String base = (baseUri != null && !baseUri.isBlank()) ? baseUri : DEFAULT_UPDATES_URL;

        try {
            Document doc = Jsoup.parse(html, base);

            // 1. Try finding dedicated XenForo/BuiltByBit update message containers
            Elements updateContainers = doc.select(
                    ".resourceUpdate, .message--resourceUpdate, li.resourceUpdate, article.resourceUpdate, " +
                    "article.message, .block-row.resourceUpdate, .resource-update-item"
            );

            if (!updateContainers.isEmpty()) {
                Element latestContainer = updateContainers.first();
                Optional<UpdateInfo> parsed = extractFromContainer(latestContainer, base);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }

            // 2. Fallback: Search globally across the document for update titles and links
            return extractFromDocument(doc, base);

        } catch (Exception e) {
            // Never throw, return empty
            return Optional.empty();
        }
    }

    private static Optional<UpdateInfo> extractFromContainer(Element container, String base) {
        // Extract Title and URL
        String title = null;
        String url = null;

        Element titleEl = container.selectFirst(
                ".resourceUpdate-title a, h2.resourceUpdate-title a, h2.message-title a, " +
                ".resourceUpdate-header a, h2.resourceUpdate-title, h3.resourceUpdate-title, " +
                "a[href*='update?update='], a[href*='/updates/']"
        );

        if (titleEl != null) {
            title = sanitize(titleEl.text());
            url = titleEl.absUrl("href");
        }

        // If title still null, look for any header tag
        if (title == null || title.isBlank()) {
            Element header = container.selectFirst("h1, h2, h3, h4");
            if (header != null) {
                title = sanitize(header.text());
                Element link = header.selectFirst("a");
                if (link != null) {
                    url = link.absUrl("href");
                }
            }
        }

        // Extract Date
        String date = null;
        Element timeEl = container.selectFirst("time.u-dt, time[datetime], time[title], .resourceUpdate-date, .message-attribution time, time");
        if (timeEl != null) {
            if (timeEl.hasAttr("title") && !timeEl.attr("title").isBlank()) {
                date = sanitize(timeEl.attr("title"));
            } else if (timeEl.hasAttr("datetime") && !timeEl.attr("datetime").isBlank()) {
                date = sanitize(timeEl.attr("datetime"));
            } else {
                date = sanitize(timeEl.text());
            }
        }

        // Extract Changelog Content
        String changelog = "";
        Element contentEl = container.selectFirst(".bbWrapper, .message-body, .message-content, .resourceUpdate-content");
        if (contentEl != null) {
            changelog = sanitize(contentEl.text());
        }

        if (url == null || url.isBlank()) {
            Element anyLink = container.selectFirst("a[href*='update']");
            if (anyLink != null) {
                url = anyLink.absUrl("href");
            }
        }
        if (url == null || url.isBlank()) {
            url = base;
        }

        if (title == null || title.isBlank()) {
            return Optional.empty();
        }

        String stableId = determineStableId(url, title, date, changelog);
        return Optional.of(new UpdateInfo(
                stableId,
                title,
                date != null ? date : "Recent",
                url,
                changelog,
                UpdateInfo.hashString(changelog),
                System.currentTimeMillis()
        ));
    }

    private static Optional<UpdateInfo> extractFromDocument(Document doc, String base) {
        // Try to find any link pointing to a specific update
        Element updateLink = doc.selectFirst("a[href*='update?update='], a[href*='/updates/']");
        String title = null;
        String url = null;

        if (updateLink != null) {
            title = sanitize(updateLink.text());
            url = updateLink.absUrl("href");
        }

        if (title == null || title.isBlank()) {
            // Look for any header mentioning CoreSMP or Update
            for (Element header : doc.select("h1, h2, h3, h4")) {
                String txt = header.text();
                if (txt.toLowerCase().contains("coresmp") || txt.toLowerCase().contains("v1.") || txt.toLowerCase().contains("update")) {
                    title = sanitize(txt);
                    Element a = header.selectFirst("a");
                    if (a != null) {
                        url = a.absUrl("href");
                    }
                    break;
                }
            }
        }

        if (title == null || title.isBlank()) {
            return Optional.empty();
        }

        String date = "Recent";
        Element timeEl = doc.selectFirst("time");
        if (timeEl != null) {
            date = sanitize(!timeEl.attr("title").isBlank() ? timeEl.attr("title") : timeEl.text());
        }

        if (url == null || url.isBlank()) {
            url = base;
        }

        String changelog = "";
        Element body = doc.selectFirst(".bbWrapper, .message-body, article");
        if (body != null) {
            changelog = sanitize(body.text());
        }

        String stableId = determineStableId(url, title, date, changelog);
        return Optional.of(new UpdateInfo(
                stableId,
                title,
                date,
                url,
                changelog,
                UpdateInfo.hashString(changelog),
                System.currentTimeMillis()
        ));
    }

    /**
     * Determines a stable identifier according to the preferred priority:
     * 1. BuiltByBit update URL/ID if available
     * 2. Otherwise update title + publication date
     * 3. Otherwise a hash of the latest update content
     */
    public static String determineStableId(String url, String title, String date, String changelog) {
        // 1. BuiltByBit update ID from URL
        if (url != null && !url.isBlank()) {
            Matcher qm = UPDATE_ID_QUERY_PATTERN.matcher(url);
            if (qm.find()) {
                return "update-" + qm.group(1);
            }
            Matcher pm = UPDATE_ID_PATH_PATTERN.matcher(url);
            if (pm.find()) {
                return "update-" + pm.group(1);
            }
        }

        // 2. Title + publication date slug
        if (title != null && !title.isBlank() && date != null && !date.isBlank()) {
            String slug = (title + "_" + date).toLowerCase()
                    .replaceAll("[^a-z0-9_]+", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
            if (!slug.isBlank()) {
                return slug;
            }
        }

        // 3. Hash of changelog/content
        return UpdateInfo.hashString(changelog != null && !changelog.isBlank() ? changelog : title + "_" + System.currentTimeMillis());
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\r", "")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
