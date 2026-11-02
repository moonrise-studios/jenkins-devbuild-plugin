package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.config.DevBuildGlobalConfiguration;
import gg.moonrise.jenkins.devbuild.webhook.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.discord.DiscordClient;
import gg.moonrise.jenkins.devbuild.discord.DiscordEmbed;
import gg.moonrise.jenkins.devbuild.discord.DiscordResponse;
import gg.moonrise.jenkins.devbuild.discord.WebhookMessage;
import gg.moonrise.jenkins.devbuild.discord.WebhookUrls;
import hudson.AbortException;
import hudson.model.Item;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Backs the "Test webhook" button: sends a small message with the values from the form.
 */
public final class WebhookTester {

    private static final Logger LOGGER = Logger.getLogger(WebhookTester.class.getName());
    private static final int TEST_COLOR = 0x5865F2;

    private WebhookTester() {
    }

    /**
     * Sends a test message. The caller has already checked permissions.
     */
    @NonNull
    public static FormValidation test(@NonNull DiscordWebhook webhook, @CheckForNull Item item) {
        String url;
        try {
            url = WebhookCredentials.resolveUrl(webhook, item);
        } catch (AbortException e) {
            return FormValidation.error(e.getMessage());
        }
        WebhookMessage message = new WebhookMessage()
                .content("Test message from the DevBuild Discord Publisher")
                .allowedMentions(List.of())
                .embed(new DiscordEmbed()
                        .title("DevBuild webhook test")
                        .description("Sent from " + (item == null ? "the global configuration" : item.getFullDisplayName())
                                + " on " + rootUrlOrName())
                        .color(TEST_COLOR)
                        .timestamp(Instant.now().toString())
                        .applyLimits());
        if (webhook.getThreadId() == null && webhook.getThreadName() != null) {
            message.threadName("DevBuild webhook test").appliedTags(webhook.appliedTagList());
        }
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        PrintStream sink = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        try (DiscordClient client = new DiscordClient(Duration.ofSeconds(global.getTimeoutSeconds()), 0, sink)) {
            DiscordResponse response = client.execute(url, webhook.getThreadId(), message);
            return FormValidation.ok("Test message sent to " + WebhookUrls.mask(url)
                    + (response.messageId() == null ? "" : " (message " + response.messageId() + ")"));
        } catch (IOException e) {
            return FormValidation.error(WebhookUrls.redact(e.getMessage(), url));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return FormValidation.error("Interrupted while sending the test message");
        } catch (RuntimeException e) {
            // Not logging the exception itself: its message could contain the webhook URL.
            LOGGER.log(Level.WARNING, "Unexpected {0} while testing a Discord webhook", e.getClass().getName());
            return FormValidation.error("Unexpected error: " + WebhookUrls.redact(e.getMessage(), url));
        }
    }

    private static String rootUrlOrName() {
        String rootUrl = Jenkins.get().getRootUrl();
        return rootUrl == null ? "Jenkins" : rootUrl;
    }
}
