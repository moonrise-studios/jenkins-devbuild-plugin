package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.DevBuildConfig;
import gg.moonrise.jenkins.devbuild.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.EmbedSettings;
import gg.moonrise.jenkins.devbuild.MessageSettings;
import gg.moonrise.jenkins.devbuild.NotifyConditions;
import gg.moonrise.jenkins.devbuild.util.TextUtils;

/**
 * A snapshot of one configuration level, used while resolving the effective settings.
 *
 * @param label human readable origin for log messages, e.g. {@code folder 'team'}
 */
public record ConfigLevel(
        @NonNull String label,
        @CheckForNull String webhookName,
        @CheckForNull DiscordWebhook webhook,
        @CheckForNull MessageSettings message,
        @CheckForNull EmbedSettings embed,
        @CheckForNull ArtifactSettings artifacts,
        @CheckForNull NotifyConditions conditions) {

    @NonNull
    public static ConfigLevel of(@NonNull String label, @NonNull DevBuildConfig config) {
        return new ConfigLevel(
                label,
                TextUtils.trimToNull(config.getWebhookName()),
                config.getWebhook(),
                config.getMessage(),
                config.getEmbed(),
                config.getArtifacts(),
                config.getConditions());
    }
}
