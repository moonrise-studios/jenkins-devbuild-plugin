package gg.moonrise.jenkins.devbuild;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * DevBuild settings on a folder. Applies to every job below it, which includes the branch jobs of Multibranch
 * Pipelines and the repositories of Organization Folders, since those are folders too.
 */
public class DevBuildFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> implements AutoPublishConfig {

    private String webhookName;
    private DiscordWebhook webhook;
    private MessageSettings message;
    private EmbedSettings embed;
    private ArtifactSettings artifacts;
    private NotifyConditions conditions;
    private boolean autoPublish = true;
    private boolean skipIfStepUsed = true;

    @DataBoundConstructor
    public DevBuildFolderProperty() {
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
    @Symbol("discordDevBuildFolder")
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "DevBuild Discord Publisher";
        }

        /**
         * The form wraps the settings in an optional block; an unchecked block removes the property.
         */
        @Override
        public AbstractFolderProperty<?> newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            if (formData == null || formData.isNullObject() || !formData.optBoolean("specified")) {
                return null;
            }
            return super.newInstance(req, formData);
        }

        public ListBoxModel doFillWebhookNameItems(@QueryParameter String webhookName) {
            return DevBuildForms.webhookNameItems(webhookName);
        }
    }
}
