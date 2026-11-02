package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.webhook.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.settings.EmbedSettings;
import gg.moonrise.jenkins.devbuild.settings.MessageSettings;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import java.time.Duration;

/**
 * Settings after inheritance has been applied; every section is present.
 *
 * @param webhook       the webhook to post to
 * @param webhookOrigin which level supplied the webhook, for log messages
 */
public record EffectiveSettings(
        @NonNull DiscordWebhook webhook,
        @NonNull String webhookOrigin,
        @NonNull MessageSettings message,
        @NonNull EmbedSettings embed,
        @NonNull ArtifactSettings artifacts,
        @NonNull NotifyConditions conditions,
        @NonNull Duration timeout,
        int maxRetries) {
}
