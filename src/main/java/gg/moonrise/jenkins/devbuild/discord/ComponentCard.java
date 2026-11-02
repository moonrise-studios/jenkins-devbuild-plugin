package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a build card as Discord Components V2, so the embed and the uploaded files fit in one message.
 *
 * <p>The optional message text (e.g. role mentions) becomes a Text Display above a Container. The Container has the
 * embed colour as its accent bar and holds, in order: the author, title and description (in a Section with the
 * thumbnail when there is one), the fields (consecutive inline fields share one Text Display, one line each), the
 * image, a separator and a {@value #DOWNLOAD_HEADER} heading followed by one File component per attachment, and the
 * footer with the timestamp. When the Section has room and the fields start with inline ones, those inline lines move
 * into the Section, so they sit beside the thumbnail instead of leaving empty space under a short title.
 *
 * <p>The card is cut down to {@link DiscordLimits#COMPONENT_TEXT_TOTAL} characters and
 * {@link DiscordLimits#COMPONENTS_TOTAL} components in the same order as {@link DiscordEmbed#applyLimits()}: the
 * description is shortened, trailing fields are dropped, then the footer, the title and finally the message text are
 * shortened. File components are never dropped.
 */
public final class ComponentCard {

    private static final int TYPE_SECTION = 9;
    private static final int TYPE_TEXT_DISPLAY = 10;
    private static final int TYPE_THUMBNAIL = 11;
    private static final int TYPE_MEDIA_GALLERY = 12;
    private static final int TYPE_FILE = 13;
    private static final int TYPE_SEPARATOR = 14;
    private static final int TYPE_CONTAINER = 17;
    private static final int SPACING_SMALL = 1;
    private static final int SECTION_TEXTS = 3;
    private static final String ATTACHMENT_SCHEME = "attachment://";
    private static final String FALLBACK_FILENAME = "file";
    private static final String DOWNLOAD_HEADER = "**Download here**";

    private final DiscordEmbed embed;
    private final List<DiscordAttachment> attachments;
    private final List<DiscordEmbed.Field> fields;
    private String messageText;
    private String title;
    private String description;
    private String footerText;

    private ComponentCard(@CheckForNull String messageText, DiscordEmbed embed, List<DiscordAttachment> attachments) {
        this.embed = embed;
        this.attachments = List.copyOf(attachments);
        this.fields = new ArrayList<>(embed.getFields());
        this.messageText = TextUtils.isBlank(messageText) ? null : TextUtils.truncate(messageText, DiscordLimits.CONTENT);
        this.title = singleLine(embed.getTitle());
        this.description = embed.getDescription();
        this.footerText = singleLine(embed.getFooterText());
    }

    /**
     * Renders the top-level components of a Components V2 message.
     *
     * @param messageText text shown above the card, or {@code null}; mentions in it obey {@code allowed_mentions}
     * @param embed       the embed to show as the card, already cut to the embed limits
     * @param attachments files shown inside the card; their filenames must be safe for {@code attachment://}
     *                    references, see {@link #withUniqueSafeNames(List)}
     * @return the components, empty when there is nothing to show
     */
    @NonNull
    public static List<Map<String, Object>> render(@CheckForNull String messageText, @NonNull DiscordEmbed embed,
                                                   @NonNull List<DiscordAttachment> attachments) {
        return new ComponentCard(messageText, embed, attachments).fitToLimits();
    }

    /**
     * Returns the attachments with filenames that can be referenced as {@code attachment://<filename>}: characters
     * other than {@code A-Z a-z 0-9 . _ -} become {@code _}, and repeated names get a {@code -2}, {@code -3}, ...
     * suffix before the extension. Use the result both for the card and for the uploaded files.
     */
    @NonNull
    public static List<DiscordAttachment> withUniqueSafeNames(@NonNull List<DiscordAttachment> attachments) {
        Set<String> used = new HashSet<>();
        List<DiscordAttachment> result = new ArrayList<>(attachments.size());
        for (DiscordAttachment attachment : attachments) {
            String name = uniqueName(TextUtils.safeFilename(attachment.filename(), FALLBACK_FILENAME), used);
            result.add(new DiscordAttachment(name, attachment.size(), attachment.source()));
        }
        return result;
    }

    /**
     * Number of characters Discord counts towards {@link DiscordLimits#COMPONENT_TEXT_TOTAL}.
     */
    public static int textLength(@NonNull List<Map<String, Object>> components) {
        int total = 0;
        for (Map<String, Object> component : components) {
            if (Integer.valueOf(TYPE_TEXT_DISPLAY).equals(component.get("type"))) {
                total += String.valueOf(component.get("content")).length();
            }
            total += textLength(children(component));
        }
        return total;
    }

    /**
     * Number of components Discord counts towards {@link DiscordLimits#COMPONENTS_TOTAL}, including nested ones.
     */
    public static int componentCount(@NonNull List<Map<String, Object>> components) {
        int total = 0;
        for (Map<String, Object> component : components) {
            total += 1 + componentCount(children(component));
        }
        return total;
    }

    private List<Map<String, Object>> fitToLimits() {
        List<Map<String, Object>> components = build();
        int excess = textExcess(components);
        if (excess > 0 && description != null) {
            description = truncateOrNull(description, description.length() - excess);
            components = build();
        }
        while (!fields.isEmpty()
                && (textExcess(components) > 0 || componentCount(components) > DiscordLimits.COMPONENTS_TOTAL)) {
            fields.remove(fields.size() - 1);
            components = build();
        }
        excess = textExcess(components);
        if (excess > 0 && footerText != null) {
            footerText = truncateOrNull(footerText, footerText.length() - excess);
            components = build();
        }
        excess = textExcess(components);
        if (excess > 0 && title != null) {
            title = truncateOrNull(title, Math.max(1, title.length() - excess));
            components = build();
        }
        excess = textExcess(components);
        if (excess > 0 && messageText != null) {
            messageText = truncateOrNull(messageText, messageText.length() - excess);
            components = build();
        }
        return components;
    }

    private List<Map<String, Object>> build() {
        List<Map<String, Object>> components = new ArrayList<>();
        if (messageText != null) {
            components.add(textDisplay(messageText));
        }
        List<Map<String, Object>> children = new ArrayList<>();
        List<Map<String, Object>> fieldTexts = fieldTexts();
        addHeader(children, fieldTexts);
        children.addAll(fieldTexts);
        if (embed.getImageUrl() != null) {
            Map<String, Object> gallery = component(TYPE_MEDIA_GALLERY);
            gallery.put("items", List.of(Map.of("media", Map.of("url", embed.getImageUrl()))));
            children.add(gallery);
        }
        if (!attachments.isEmpty()) {
            Map<String, Object> separator = component(TYPE_SEPARATOR);
            separator.put("divider", true);
            separator.put("spacing", SPACING_SMALL);
            children.add(separator);
            children.add(textDisplay(DOWNLOAD_HEADER));
            for (DiscordAttachment attachment : attachments) {
                Map<String, Object> file = component(TYPE_FILE);
                file.put("file", Map.of("url", ATTACHMENT_SCHEME + attachment.filename()));
                children.add(file);
            }
        }
        String footer = footerLine();
        if (footer != null) {
            children.add(textDisplay(footer));
        }
        if (!children.isEmpty()) {
            Map<String, Object> container = component(TYPE_CONTAINER);
            container.put("components", children);
            if (embed.getColor() != null) {
                container.put("accent_color", embed.getColor());
            }
            components.add(container);
        }
        return components;
    }

    /**
     * Adds the author, title and description. With a thumbnail they go into a Section, which also takes the leading
     * inline fields from {@code fieldTexts} while it has room, so the thumbnail does not tower over a short title.
     */
    private void addHeader(List<Map<String, Object>> children, List<Map<String, Object>> fieldTexts) {
        List<Map<String, Object>> texts = new ArrayList<>(SECTION_TEXTS);
        String authorName = singleLine(embed.getAuthorName());
        if (authorName != null) {
            texts.add(textDisplay("-# " + link(authorName, embed.getAuthorUrl())));
        }
        if (title != null) {
            texts.add(textDisplay("### " + link(title, embed.getUrl())));
        }
        if (description != null) {
            texts.add(textDisplay(description));
        }
        String thumbnailUrl = embed.getThumbnailUrl();
        if (thumbnailUrl == null || texts.isEmpty()) {
            children.addAll(texts);
            return;
        }
        if (texts.size() < SECTION_TEXTS && !fields.isEmpty() && fields.get(0).inline()) {
            texts.add(fieldTexts.remove(0));
        }
        Map<String, Object> thumbnail = component(TYPE_THUMBNAIL);
        thumbnail.put("media", Map.of("url", thumbnailUrl));
        Map<String, Object> section = component(TYPE_SECTION);
        section.put("components", texts);
        section.put("accessory", thumbnail);
        children.add(section);
    }

    /**
     * One Text Display per non-inline field and per run of consecutive inline fields, in field order.
     */
    private List<Map<String, Object>> fieldTexts() {
        List<Map<String, Object>> children = new ArrayList<>();
        List<String> inlineLines = new ArrayList<>();
        for (DiscordEmbed.Field field : fields) {
            boolean unnamed = DiscordEmbed.EMPTY_FIELD.equals(field.name());
            if (field.inline()) {
                inlineLines.add(unnamed ? field.value() : "**" + field.name() + ":** " + field.value());
                continue;
            }
            flushInline(children, inlineLines);
            children.add(textDisplay(unnamed ? field.value() : "**" + field.name() + "**\n" + field.value()));
        }
        flushInline(children, inlineLines);
        return children;
    }

    private static void flushInline(List<Map<String, Object>> children, List<String> inlineLines) {
        if (!inlineLines.isEmpty()) {
            children.add(textDisplay(String.join("\n", inlineLines)));
            inlineLines.clear();
        }
    }

    @CheckForNull
    private String footerLine() {
        String stamp = timestampTag(embed.getTimestamp());
        if (footerText == null && stamp == null) {
            return null;
        }
        if (footerText == null) {
            return "-# " + stamp;
        }
        return stamp == null ? "-# " + footerText : "-# " + footerText + " · " + stamp;
    }

    @CheckForNull
    private static String timestampTag(@CheckForNull String isoTimestamp) {
        if (isoTimestamp == null) {
            return null;
        }
        try {
            return "<t:" + Instant.parse(isoTimestamp.trim()).getEpochSecond() + ":f>";
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private int textExcess(List<Map<String, Object>> components) {
        return textLength(components) - DiscordLimits.COMPONENT_TEXT_TOTAL;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> component) {
        List<Map<String, Object>> children = new ArrayList<>();
        if (component.get("components") instanceof List<?> nested) {
            children.addAll((List<Map<String, Object>>) nested);
        }
        if (component.get("accessory") instanceof Map<?, ?> accessory) {
            children.add((Map<String, Object>) accessory);
        }
        return children;
    }

    private static Map<String, Object> component(int type) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", type);
        return component;
    }

    private static Map<String, Object> textDisplay(String content) {
        Map<String, Object> text = component(TYPE_TEXT_DISPLAY);
        text.put("content", content);
        return text;
    }

    /**
     * A Markdown link, or the plain label without a URL. Brackets in the label and parentheses or spaces in the URL
     * would end the link early, so they are escaped.
     */
    private static String link(String label, @CheckForNull String url) {
        if (url == null) {
            return label;
        }
        String safeLabel = label.replace("[", "\\[").replace("]", "\\]");
        String safeUrl = url.replace(" ", "%20").replace("(", "%28").replace(")", "%29");
        return "[" + safeLabel + "](" + safeUrl + ")";
    }

    @CheckForNull
    private static String singleLine(@CheckForNull String value) {
        return value == null ? null : TextUtils.trimToNull(value.replaceAll("\\s*[\\r\\n]+\\s*", " "));
    }

    @CheckForNull
    private static String truncateOrNull(String value, int max) {
        return max <= 0 ? null : TextUtils.truncate(value, max);
    }

    private static String uniqueName(String name, Set<String> used) {
        if (used.add(name)) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; ; n++) {
            String candidate = stem + "-" + n + extension;
            if (used.add(candidate)) {
                return candidate;
            }
        }
    }
}
