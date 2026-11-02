package gg.moonrise.jenkins.devbuild.discord;


/**
 * Discord API limits. Exceeding any of these makes Discord reject the whole request with HTTP 400.
 */
public final class DiscordLimits {

    public static final int CONTENT = 2000;
    public static final int USERNAME = 80;
    public static final int THREAD_NAME = 100;
    public static final int EMBED_TITLE = 256;
    public static final int EMBED_DESCRIPTION = 4096;
    public static final int EMBED_FIELDS = 25;
    public static final int FIELD_NAME = 256;
    public static final int FIELD_VALUE = 1024;
    public static final int FOOTER_TEXT = 2048;
    public static final int AUTHOR_NAME = 256;
    public static final int EMBED_TOTAL = 6000;
    public static final int ATTACHMENTS_PER_MESSAGE = 10;
    public static final int APPLIED_TAGS = 5;
    public static final long MAX_REQUEST_BYTES = 25L * 1024 * 1024;
    /** Components per message, counting nested ones. */
    public static final int COMPONENTS_TOTAL = 40;
    /** Characters across all Text Display components of a message. */
    public static final int COMPONENT_TEXT_TOTAL = 4000;

    /** Message flag: do not trigger push or desktop notifications. */
    public static final int FLAG_SUPPRESS_NOTIFICATIONS = 1 << 12;
    /** Message flag: the message is laid out with components only; content and embeds are not allowed. */
    public static final int FLAG_IS_COMPONENTS_V2 = 1 << 15;

    private DiscordLimits() {
    }
}
