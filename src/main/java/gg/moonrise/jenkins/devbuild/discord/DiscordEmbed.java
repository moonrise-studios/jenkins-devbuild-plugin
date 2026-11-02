package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Discord embed. Text is kept as given until {@link #applyLimits()} cuts it down to Discord's limits.
 */
public final class DiscordEmbed {

    /** Zero width space: Discord rejects empty field names and values. */
    private static final String EMPTY_FIELD = "​";

    private String title;
    private String description;
    private String url;
    private Integer color;
    private String timestamp;
    private String authorName;
    private String authorUrl;
    private String authorIconUrl;
    private String footerText;
    private String footerIconUrl;
    private String thumbnailUrl;
    private String imageUrl;
    private final List<Field> fields = new ArrayList<>();

    /**
     * An embed field.
     *
     * @param name   field title
     * @param value  field body, Markdown allowed
     * @param inline whether Discord may lay the field out next to others
     */
    public record Field(@NonNull String name, @NonNull String value, boolean inline) {
    }

    public DiscordEmbed title(@CheckForNull String value) {
        this.title = TextUtils.trimToNull(value);
        return this;
    }

    public DiscordEmbed description(@CheckForNull String value) {
        this.description = TextUtils.isBlank(value) ? null : value;
        return this;
    }

    public DiscordEmbed url(@CheckForNull String value) {
        this.url = WebhookUrls.isHttpUrl(value) ? value.trim() : null;
        return this;
    }

    public DiscordEmbed color(@CheckForNull Integer value) {
        this.color = value;
        return this;
    }

    public DiscordEmbed timestamp(@CheckForNull String isoTimestamp) {
        this.timestamp = isoTimestamp;
        return this;
    }

    public DiscordEmbed author(@CheckForNull String name, @CheckForNull String link, @CheckForNull String iconUrl) {
        this.authorName = TextUtils.trimToNull(name);
        this.authorUrl = WebhookUrls.isHttpUrl(link) ? link.trim() : null;
        this.authorIconUrl = WebhookUrls.isHttpUrl(iconUrl) ? iconUrl.trim() : null;
        return this;
    }

    public DiscordEmbed footer(@CheckForNull String text, @CheckForNull String iconUrl) {
        this.footerText = TextUtils.trimToNull(text);
        this.footerIconUrl = WebhookUrls.isHttpUrl(iconUrl) ? iconUrl.trim() : null;
        return this;
    }

    public DiscordEmbed thumbnail(@CheckForNull String value) {
        this.thumbnailUrl = WebhookUrls.isHttpUrl(value) ? value.trim() : null;
        return this;
    }

    public DiscordEmbed image(@CheckForNull String value) {
        this.imageUrl = WebhookUrls.isHttpUrl(value) ? value.trim() : null;
        return this;
    }

    public DiscordEmbed field(@CheckForNull String name, @CheckForNull String value, boolean inline) {
        String safeName = TextUtils.isBlank(name) ? EMPTY_FIELD : name;
        String safeValue = TextUtils.isBlank(value) ? EMPTY_FIELD : value;
        fields.add(new Field(safeName, safeValue, inline));
        return this;
    }

    @CheckForNull
    public String getTitle() {
        return title;
    }

    @CheckForNull
    public String getDescription() {
        return description;
    }

    @CheckForNull
    public String getFooterText() {
        return footerText;
    }

    @NonNull
    public List<Field> getFields() {
        return List.copyOf(fields);
    }

    /**
     * Cuts every part to its Discord limit, then trims the description and drops trailing fields until the whole
     * embed fits in {@link DiscordLimits#EMBED_TOTAL} characters.
     */
    public DiscordEmbed applyLimits() {
        title = truncateOrNull(title, DiscordLimits.EMBED_TITLE);
        description = truncateOrNull(description, DiscordLimits.EMBED_DESCRIPTION);
        authorName = truncateOrNull(authorName, DiscordLimits.AUTHOR_NAME);
        footerText = truncateOrNull(footerText, DiscordLimits.FOOTER_TEXT);
        while (fields.size() > DiscordLimits.EMBED_FIELDS) {
            fields.remove(fields.size() - 1);
        }
        fields.replaceAll(field -> new Field(
                TextUtils.truncate(field.name(), DiscordLimits.FIELD_NAME),
                TextUtils.truncate(field.value(), DiscordLimits.FIELD_VALUE),
                field.inline()));

        int excess = totalLength() - DiscordLimits.EMBED_TOTAL;
        if (excess > 0 && description != null) {
            int keep = Math.max(0, description.length() - excess);
            description = keep == 0 ? null : TextUtils.truncate(description, keep);
        }
        while (totalLength() > DiscordLimits.EMBED_TOTAL && !fields.isEmpty()) {
            fields.remove(fields.size() - 1);
        }
        excess = totalLength() - DiscordLimits.EMBED_TOTAL;
        if (excess > 0 && footerText != null) {
            footerText = truncateOrNull(footerText, Math.max(0, footerText.length() - excess));
        }
        excess = totalLength() - DiscordLimits.EMBED_TOTAL;
        if (excess > 0 && title != null) {
            title = truncateOrNull(title, Math.max(1, title.length() - excess));
        }
        return this;
    }

    /**
     * Number of characters Discord counts towards the 6000 character embed limit.
     */
    public int totalLength() {
        int total = length(title) + length(description) + length(authorName) + length(footerText);
        for (Field field : fields) {
            total += field.name().length() + field.value().length();
        }
        return total;
    }

    /**
     * Returns {@code true} when the embed has nothing Discord would render.
     */
    public boolean isEmpty() {
        return title == null && description == null && fields.isEmpty() && authorName == null && footerText == null
                && imageUrl == null && thumbnailUrl == null;
    }

    /**
     * Converts the embed into the JSON object structure Discord expects.
     */
    @NonNull
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        putIfPresent(json, "title", title);
        putIfPresent(json, "description", description);
        putIfPresent(json, "url", url);
        if (color != null) {
            json.put("color", color);
        }
        putIfPresent(json, "timestamp", timestamp);
        if (authorName != null) {
            Map<String, Object> author = new LinkedHashMap<>();
            author.put("name", authorName);
            putIfPresent(author, "url", authorUrl);
            putIfPresent(author, "icon_url", authorIconUrl);
            json.put("author", author);
        }
        if (footerText != null) {
            Map<String, Object> footer = new LinkedHashMap<>();
            footer.put("text", footerText);
            putIfPresent(footer, "icon_url", footerIconUrl);
            json.put("footer", footer);
        }
        if (thumbnailUrl != null) {
            json.put("thumbnail", Map.of("url", thumbnailUrl));
        }
        if (imageUrl != null) {
            json.put("image", Map.of("url", imageUrl));
        }
        if (!fields.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Field field : fields) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", field.name());
                entry.put("value", field.value());
                entry.put("inline", field.inline());
                list.add(entry);
            }
            json.put("fields", list);
        }
        return json;
    }

    private static void putIfPresent(Map<String, Object> json, String key, @CheckForNull String value) {
        if (value != null) {
            json.put(key, value);
        }
    }

    private static int length(@CheckForNull String value) {
        return value == null ? 0 : value.length();
    }

    @CheckForNull
    private static String truncateOrNull(@CheckForNull String value, int max) {
        if (value == null || max <= 0) {
            return null;
        }
        return TextUtils.truncate(value, max);
    }
}
