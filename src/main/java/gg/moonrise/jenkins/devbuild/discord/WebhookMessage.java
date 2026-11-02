package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One webhook execution: the JSON payload plus the files uploaded with it.
 */
public final class WebhookMessage {

    private String content;
    private String username;
    private String avatarUrl;
    private boolean tts;
    private int flags;
    private String threadName;
    private final List<String> appliedTags = new ArrayList<>();
    private final List<String> allowedMentions = new ArrayList<>();
    private final List<DiscordEmbed> embeds = new ArrayList<>();
    private final List<DiscordAttachment> attachments = new ArrayList<>();

    public WebhookMessage content(@CheckForNull String value) {
        this.content = TextUtils.isBlank(value) ? null : TextUtils.truncate(value, DiscordLimits.CONTENT);
        return this;
    }

    public WebhookMessage username(@CheckForNull String value) {
        this.username = TextUtils.isBlank(value) ? null : TextUtils.truncate(value.trim(), DiscordLimits.USERNAME);
        return this;
    }

    public WebhookMessage avatarUrl(@CheckForNull String value) {
        this.avatarUrl = WebhookUrls.isHttpUrl(value) ? value.trim() : null;
        return this;
    }

    public WebhookMessage tts(boolean value) {
        this.tts = value;
        return this;
    }

    public WebhookMessage flags(int value) {
        this.flags = value;
        return this;
    }

    public WebhookMessage threadName(@CheckForNull String value) {
        this.threadName = TextUtils.isBlank(value) ? null : TextUtils.truncate(value.trim(), DiscordLimits.THREAD_NAME);
        return this;
    }

    public WebhookMessage appliedTags(@NonNull List<String> tags) {
        appliedTags.clear();
        appliedTags.addAll(tags.subList(0, Math.min(tags.size(), DiscordLimits.APPLIED_TAGS)));
        return this;
    }

    /**
     * Sets the mention types Discord may resolve: any of {@code users}, {@code roles}, {@code everyone}.
     */
    public WebhookMessage allowedMentions(@NonNull List<String> parse) {
        allowedMentions.clear();
        allowedMentions.addAll(parse);
        return this;
    }

    public WebhookMessage embed(@NonNull DiscordEmbed embed) {
        embeds.add(embed);
        return this;
    }

    public WebhookMessage attachment(@NonNull DiscordAttachment attachment) {
        attachments.add(attachment);
        return this;
    }

    @CheckForNull
    public String getContent() {
        return content;
    }

    @CheckForNull
    public String getThreadName() {
        return threadName;
    }

    @NonNull
    public List<DiscordEmbed> getEmbeds() {
        return List.copyOf(embeds);
    }

    @NonNull
    public List<DiscordAttachment> getAttachments() {
        return List.copyOf(attachments);
    }

    /**
     * Returns {@code true} when Discord would reject the message for having nothing to show.
     */
    public boolean isEmpty() {
        return content == null && embeds.isEmpty() && attachments.isEmpty();
    }

    /**
     * Builds the {@code payload_json} object, including the {@code attachments} array that maps each
     * {@code files[n]} part to its filename.
     */
    @NonNull
    public Map<String, Object> toPayload() {
        Map<String, Object> json = new LinkedHashMap<>();
        if (content != null) {
            json.put("content", content);
        }
        if (username != null) {
            json.put("username", username);
        }
        if (avatarUrl != null) {
            json.put("avatar_url", avatarUrl);
        }
        if (tts) {
            json.put("tts", true);
        }
        if (!embeds.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (DiscordEmbed embed : embeds) {
                list.add(embed.toJson());
            }
            json.put("embeds", list);
        }
        json.put("allowed_mentions", Map.of("parse", List.copyOf(allowedMentions)));
        if (flags != 0) {
            json.put("flags", flags);
        }
        if (threadName != null) {
            json.put("thread_name", threadName);
        }
        if (!appliedTags.isEmpty()) {
            json.put("applied_tags", List.copyOf(appliedTags));
        }
        if (!attachments.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < attachments.size(); i++) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", i);
                entry.put("filename", attachments.get(i).filename());
                list.add(entry);
            }
            json.put("attachments", list);
        }
        return json;
    }
}
