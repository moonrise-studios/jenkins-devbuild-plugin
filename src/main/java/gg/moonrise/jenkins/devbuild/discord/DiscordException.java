package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import java.io.IOException;

/**
 * Discord rejected a webhook request. The message never contains the webhook URL or token.
 */
public class DiscordException extends IOException {

    private static final long serialVersionUID = 1L;
    private static final int MAX_BODY_IN_MESSAGE = 500;

    private final int statusCode;

    public DiscordException(int statusCode, @NonNull String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Builds an explanatory exception for a non-success HTTP status.
     */
    @NonNull
    public static DiscordException forStatus(int statusCode, @CheckForNull String body, @NonNull String maskedUrl) {
        String detail = TextUtils.truncate(TextUtils.nullToEmpty(body).strip(), MAX_BODY_IN_MESSAGE);
        String reason = switch (statusCode) {
            case 401 -> "Discord rejected the webhook token (HTTP 401). The webhook URL is invalid; copy it again from Discord.";
            case 403 -> "Discord refused the request (HTTP 403). The webhook may not have access to the target channel or thread.";
            case 404 -> "Discord could not find the webhook or thread (HTTP 404). The webhook was probably deleted, "
                    + "or the thread id is wrong.";
            case 413 -> "Discord rejected the upload as too large (HTTP 413). Lower maxUploadSizeMb for this server.";
            case 429 -> "Discord kept rate limiting the webhook (HTTP 429) after all retries.";
            default -> "Discord returned HTTP " + statusCode + ".";
        };
        String message = reason + " Webhook: " + maskedUrl + (detail.isEmpty() ? "" : " Response: " + detail);
        return new DiscordException(statusCode, message);
    }

    /**
     * Returns {@code true} for statuses where retrying cannot help.
     */
    public boolean isPermanent() {
        return statusCode == 401 || statusCode == 403 || statusCode == 404;
    }
}
