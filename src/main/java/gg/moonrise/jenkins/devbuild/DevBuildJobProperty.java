package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Job;
import hudson.util.ListBoxModel;
import jenkins.model.OptionalJobProperty;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Per-job DevBuild settings. Works for every job type, including Pipeline jobs via
 * {@code properties([discordDevBuildJob(...)])}.
 */
public class DevBuildJobProperty extends OptionalJobProperty<Job<?, ?>> implements AutoPublishConfig {

    private String webhookName;
    private DiscordWebhook webhook;
    private MessageSettings message;
    private EmbedSettings embed;
    private ArtifactSettings artifacts;
    private NotifyConditions conditions;
    private boolean autoPublish = true;
    private boolean skipIfStepUsed = true;

    @DataBoundConstructor
    public DevBuildJobProperty() {
    }

    @Override
    @CheckForNull
    public String getWebhookName() {
        return webhookName;
    }

    @DataBoundSetter
    public void setWebhookName(@CheckForNull String webhookName) {
        this.webhookName = TextUtils.trimToNull(webhookName);
    }

    @Override
    @CheckForNull
    public DiscordWebhook getWebhook() {
        return webhook;
    }

    @DataBoundSetter
    public void setWebhook(@CheckForNull DiscordWebhook webhook) {
        this.webhook = webhook;
    }

    @Override
    @CheckForNull
    public MessageSettings getMessage() {
        return message;
    }

    @DataBoundSetter
    public void setMessage(@CheckForNull MessageSettings message) {
        this.message = message;
    }

    @Override
    @CheckForNull
    public EmbedSettings getEmbed() {
        return embed;
    }

    @DataBoundSetter
    public void setEmbed(@CheckForNull EmbedSettings embed) {
        this.embed = embed;
    }

    @Override
    @CheckForNull
    public ArtifactSettings getArtifacts() {
        return artifacts;
    }

    @DataBoundSetter
    public void setArtifacts(@CheckForNull ArtifactSettings artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    @CheckForNull
    public NotifyConditions getConditions() {
        return conditions;
    }

    @DataBoundSetter
    public void setConditions(@CheckForNull NotifyConditions conditions) {
        this.conditions = conditions;
    }

    @Override
    public boolean isAutoPublish() {
        return autoPublish;
    }

    @DataBoundSetter
    public void setAutoPublish(boolean autoPublish) {
        this.autoPublish = autoPublish;
    }

    @Override
    public boolean isSkipIfStepUsed() {
        return skipIfStepUsed;
    }

    @DataBoundSetter
    public void setSkipIfStepUsed(boolean skipIfStepUsed) {
        this.skipIfStepUsed = skipIfStepUsed;
    }

    @Extension
    @Symbol("discordDevBuildJob")
    public static class DescriptorImpl extends OptionalJobPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "DevBuild Discord Publisher";
        }

        public ListBoxModel doFillWebhookNameItems(@QueryParameter String webhookName) {
            return DevBuildForms.webhookNameItems(webhookName);
        }
    }
}
