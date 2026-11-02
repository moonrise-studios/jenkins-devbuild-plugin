package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * One level of DevBuild configuration: the step or publisher, a job property, a folder property or the global
 * defaults. Every value is optional; a {@code null} value inherits the whole value from the next level.
 */
public interface DevBuildConfig {

    /**
     * Name of a webhook defined in the global configuration, or {@code null} to inherit.
     */
    @CheckForNull
    String getWebhookName();

    /**
     * Webhook defined at this level. Takes precedence over {@link #getWebhookName()} at the same level.
     */
    @CheckForNull
    DiscordWebhook getWebhook();

    @CheckForNull
    MessageSettings getMessage();

    @CheckForNull
    EmbedSettings getEmbed();

    @CheckForNull
    ArtifactSettings getArtifacts();

    @CheckForNull
    NotifyConditions getConditions();
}
