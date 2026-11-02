package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * The message Discord created, returned because requests use {@code wait=true}.
 *
 * @param messageId id of the created message, or {@code null} if the body could not be read
 * @param channelId channel or thread the message landed in; for a new forum post this is the new thread's id
 */
public record DiscordResponse(@CheckForNull String messageId, @CheckForNull String channelId) {

    /**
     * Reads the message id and channel id from a Discord message object.
     */
    @NonNull
    public static DiscordResponse parse(@CheckForNull String body) {
        if (body == null || body.isBlank()) {
            return new DiscordResponse(null, null);
        }
        try {
            JSONObject json = JSONObject.fromObject(body);
            return new DiscordResponse(stringOrNull(json, "id"), stringOrNull(json, "channel_id"));
        } catch (JSONException e) {
            return new DiscordResponse(null, null);
        }
    }

    @CheckForNull
    private static String stringOrNull(JSONObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        String value = json.optString(key, null);
        return value == null || value.isEmpty() || "null".equals(value) ? null : value;
    }
}
