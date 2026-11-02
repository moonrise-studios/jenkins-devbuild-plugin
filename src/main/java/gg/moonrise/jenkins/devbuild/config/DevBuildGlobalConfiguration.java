package gg.moonrise.jenkins.devbuild.config;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.settings.EmbedSettings;
import gg.moonrise.jenkins.devbuild.settings.MessageSettings;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import gg.moonrise.jenkins.devbuild.webhook.NamedWebhook;
import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

/**
 * Global DevBuild settings: named webhooks, the default webhook and default sections inherited by every job.
 */
@Extension
@Symbol("discordDevBuild")
public final class DevBuildGlobalConfiguration extends GlobalConfiguration {

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAX_RETRIES = 3;

    private List<NamedWebhook> webhooks = new ArrayList<>();
    private String defaultWebhookName;
    private MessageSettings message;
    private EmbedSettings embed;
    private ArtifactSettings artifacts;
    private NotifyConditions conditions;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private int maxRetries = DEFAULT_MAX_RETRIES;

    public DevBuildGlobalConfiguration() {
        load();
    }

    @NonNull
    public static DevBuildGlobalConfiguration get() {
        return ExtensionList.lookupSingleton(DevBuildGlobalConfiguration.class);
    }

    @NonNull
    public List<NamedWebhook> getWebhooks() {
        return webhooks == null ? List.of() : List.copyOf(webhooks);
    }

    @DataBoundSetter
    public void setWebhooks(@CheckForNull List<NamedWebhook> webhooks) {
        this.webhooks = webhooks == null ? new ArrayList<>() : new ArrayList<>(webhooks);
        save();
    }

    @CheckForNull
    public String getDefaultWebhookName() {
        return defaultWebhookName;
    }

    @DataBoundSetter
    public void setDefaultWebhookName(@CheckForNull String defaultWebhookName) {
        this.defaultWebhookName = TextUtils.trimToNull(defaultWebhookName);
        save();
    }

    @CheckForNull
    public MessageSettings getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setMessage(@CheckForNull MessageSettings message) {
        this.message = message;
        save();
    }

    @CheckForNull
    public EmbedSettings getEmbed() {
        return embed;
    }

    @DataBoundSetter
    public void setEmbed(@CheckForNull EmbedSettings embed) {
        this.embed = embed;
        save();
    }

    @CheckForNull
    public ArtifactSettings getArtifacts() {
        return artifacts;
    }

    @DataBoundSetter
    public void setArtifacts(@CheckForNull ArtifactSettings artifacts) {
        this.artifacts = artifacts;
        save();
    }

    @CheckForNull
    public NotifyConditions getConditions() {
        return conditions;
    }

    @DataBoundSetter
    public void setConditions(@CheckForNull NotifyConditions conditions) {
        this.conditions = conditions;
        save();
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    @DataBoundSetter
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds < 1 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        save();
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    @DataBoundSetter
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
        save();
    }

    /**
     * Finds a named webhook.
     */
    @NonNull
    public Optional<NamedWebhook> findWebhook(@CheckForNull String name) {
        if (TextUtils.isBlank(name)) {
            return Optional.empty();
        }
        String trimmed = name.trim();
        return getWebhooks().stream().filter(webhook -> webhook.getName().equals(trimmed)).findFirst();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        try (BulkChange bc = new BulkChange(this)) {
            webhooks = new ArrayList<>();
            defaultWebhookName = null;
            message = null;
            embed = null;
            artifacts = null;
            conditions = null;
            req.bindJSON(this, json);
            bc.commit();
        } catch (IOException e) {
            throw new FormException("Could not save the DevBuild configuration: " + e.getMessage(), e, "webhooks");
        }
        return true;
    }

    public ListBoxModel doFillDefaultWebhookNameItems(@QueryParameter String defaultWebhookName) {
        ListBoxModel items = new ListBoxModel();
        items.add("(none)", "");
        for (NamedWebhook webhook : getWebhooks()) {
            items.add(webhook.getName());
        }
        if (!TextUtils.isBlank(defaultWebhookName) && findWebhook(defaultWebhookName).isEmpty()) {
            items.add(defaultWebhookName + " (not defined)", defaultWebhookName);
        }
        return items;
    }

    @POST
    public FormValidation doCheckDefaultWebhookName(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        if (TextUtils.isBlank(value)) {
            return FormValidation.ok();
        }
        return findWebhook(value).isPresent()
                ? FormValidation.ok()
                : FormValidation.warning("Save the named webhook first, then select it here");
    }

    public FormValidation doCheckTimeoutSeconds(@QueryParameter int value) {
        return DevBuildForms.checkPositive(value, 300, "The timeout");
    }

    public FormValidation doCheckMaxRetries(@QueryParameter int value) {
        if (value < 0) {
            return FormValidation.error("Must be 0 or more");
        }
        return value > 10 ? FormValidation.warning("More than 10 retries can hold a build for minutes") : FormValidation.ok();
    }
}
