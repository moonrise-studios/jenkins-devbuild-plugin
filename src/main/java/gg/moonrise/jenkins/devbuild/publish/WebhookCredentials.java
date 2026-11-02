package gg.moonrise.jenkins.devbuild.publish;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.webhook.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.discord.WebhookUrls;
import hudson.AbortException;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.Secret;
import java.util.List;
import java.util.Optional;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.springframework.security.core.Authentication;

/**
 * Turns a {@link DiscordWebhook} into the webhook URL, reading Secret text credentials where configured.
 */
public final class WebhookCredentials {

    private WebhookCredentials() {
    }

    /**
     * Returns {@code true} when the webhook names a credential or holds a URL itself.
     */
    public static boolean hasUrlSource(@NonNull DiscordWebhook webhook) {
        return webhook.getCredentialsId() != null || webhook.getWebhookUrl() != null;
    }

    /**
     * Resolves the URL for a build. Credentials are looked up in the context of the run, so folder scoped credentials
     * work; {@link CredentialsProvider#findCredentialById} also records the usage while the build log is open.
     */
    @NonNull
    public static String resolveUrl(@NonNull DiscordWebhook webhook, @NonNull Run<?, ?> run) throws AbortException {
        String credentialsId = webhook.getCredentialsId();
        if (credentialsId != null) {
            StringCredentials credentials =
                    CredentialsProvider.findCredentialById(credentialsId, StringCredentials.class, run);
            if (credentials == null) {
                throw new AbortException("[DevBuild] Secret text credential '" + credentialsId
                        + "' for the Discord webhook was not found or is not visible to " + run.getParent().getFullName());
            }
            return validate(credentials.getSecret().getPlainText(), "credential '" + credentialsId + "'");
        }
        return fromInlineUrl(webhook);
    }

    /**
     * Resolves the URL in the context of an item (or Jenkins when {@code null}), used by the "Test webhook" button.
     */
    @NonNull
    public static String resolveUrl(@NonNull DiscordWebhook webhook, @CheckForNull Item item) throws AbortException {
        String credentialsId = webhook.getCredentialsId();
        if (credentialsId != null) {
            StringCredentials credentials = lookupForItem(credentialsId, item)
                    .orElseThrow(() -> new AbortException("Secret text credential '" + credentialsId + "' was not found"));
            return validate(credentials.getSecret().getPlainText(), "credential '" + credentialsId + "'");
        }
        return fromInlineUrl(webhook);
    }

    /**
     * Finds a Secret text credential visible to the item, or to Jenkins when the item is {@code null}.
     */
    @NonNull
    public static Optional<StringCredentials> lookupForItem(@NonNull String credentialsId, @CheckForNull Item item) {
        if (item == null) {
            return Optional.ofNullable(CredentialsProvider.findCredentialByIdInItemGroup(
                    credentialsId, StringCredentials.class, Jenkins.get(), ACL.SYSTEM2, List.of()));
        }
        Authentication authentication = item instanceof Queue.Task task ? Tasks.getAuthenticationOf2(task) : ACL.SYSTEM2;
        return Optional.ofNullable(CredentialsProvider.findCredentialByIdInItem(
                credentialsId, StringCredentials.class, item, authentication, List.of()));
    }

    private static String fromInlineUrl(DiscordWebhook webhook) throws AbortException {
        Secret secret = webhook.getWebhookUrl();
        if (secret == null) {
            throw new AbortException("[DevBuild] The Discord webhook has neither a credentialsId nor a webhookUrl");
        }
        return validate(secret.getPlainText(), "webhookUrl");
    }

    private static String validate(String url, String origin) throws AbortException {
        String trimmed = url.trim();
        if (!WebhookUrls.isHttpUrl(trimmed)) {
            throw new AbortException("[DevBuild] The Discord webhook URL from " + origin + " is not a valid http(s) URL");
        }
        return trimmed;
    }
}
