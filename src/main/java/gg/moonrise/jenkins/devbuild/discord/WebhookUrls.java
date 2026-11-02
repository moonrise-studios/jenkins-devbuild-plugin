package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validation and masking of webhook URLs. The token in a webhook URL is a secret, so it must never reach a log.
 */
public final class WebhookUrls {

    private static final Pattern DISCORD_WEBHOOK = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api/(?:v\\d+/)?webhooks/\\d+/[A-Za-z0-9_.-]+/?$");
    private static final Pattern WEBHOOK_PATH = Pattern.compile("^(.*/webhooks/)([^/]+)/([^/?#]+)(.*)$");
    private static final int VISIBLE_ID_CHARS = 4;
    private static final String MASK = "****";

    private WebhookUrls() {
    }

    /**
     * Returns {@code true} when the URL has the shape of a Discord webhook URL.
     */
    public static boolean isDiscordWebhookUrl(@CheckForNull String url) {
        return url != null && DISCORD_WEBHOOK.matcher(url.trim()).matches();
    }

    /**
     * Returns {@code true} for an absolute {@code http} or {@code https} URL with a host.
     */
    public static boolean isHttpUrl(@CheckForNull String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT)) || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Masks a webhook URL for logging, e.g. {@code https://discord.com/api/webhooks/1234…/****}.
     */
    @NonNull
    public static String mask(@CheckForNull String url) {
        if (url == null || url.isBlank()) {
            return "(none)";
        }
        String trimmed = url.trim();
        String withoutQuery = trimmed.split("[?#]", 2)[0];
        Matcher matcher = WEBHOOK_PATH.matcher(withoutQuery);
        if (matcher.matches()) {
            String id = matcher.group(2);
            String visible = id.length() > VISIBLE_ID_CHARS ? id.substring(0, VISIBLE_ID_CHARS) + "…" : id;
            return matcher.group(1) + visible + "/" + MASK;
        }
        try {
            URI uri = new URI(trimmed);
            if (uri.getScheme() != null && uri.getHost() != null) {
                String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";
                return uri.getScheme() + "://" + uri.getHost() + port + "/" + MASK;
            }
        } catch (URISyntaxException e) {
            // fall through to the fully masked value
        }
        return MASK;
    }

    /**
     * Replaces every occurrence of the webhook URL (and its token) in a message with the masked form.
     */
    @NonNull
    public static String redact(@CheckForNull String message, @CheckForNull String url) {
        if (message == null) {
            return "";
        }
        if (url == null || url.isBlank()) {
            return message;
        }
        String trimmed = url.trim();
        String result = message.replace(trimmed, mask(trimmed));
        Matcher matcher = WEBHOOK_PATH.matcher(trimmed.split("[?#]", 2)[0]);
        if (matcher.matches()) {
            result = result.replace(matcher.group(3), MASK);
        }
        return result;
    }
}
