package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.publish.DevBuildNotifier;
import gg.moonrise.jenkins.devbuild.publish.EffectiveSettings;
import gg.moonrise.jenkins.devbuild.publish.PublishOutcome;
import gg.moonrise.jenkins.devbuild.publish.SettingsResolver;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Post-build action and Pipeline step {@code discordDevBuild}: posts the build to Discord with its jar files.
 *
 * <p>Configuration errors (no webhook, unknown named webhook, missing credential, oversize policy FAIL) fail the
 * step. Discord being unreachable or rejecting the request is logged and does not fail the build.
 */
public class DevBuildPublisher extends Notifier implements SimpleBuildStep, DevBuildConfig {

    private static final Set<String> RESULTS = Set.of("SUCCESS", "UNSTABLE", "FAILURE", "ABORTED", "NOT_BUILT");

    private String webhookName;
    private DiscordWebhook webhook;
    private MessageSettings message;
    private EmbedSettings embed;
    private ArtifactSettings artifacts;
    private NotifyConditions conditions;
    private String result;

    @DataBoundConstructor
    public DevBuildPublisher() {
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

    /**
     * Result to report instead of the build's current result, e.g. {@code FAILURE} in a Pipeline {@code catch} block.
     */
    @CheckForNull
    public String getResult() {
        return result;
    }

    @DataBoundSetter
    public void setResult(@CheckForNull String result) {
        String trimmed = TextUtils.trimToNull(result);
        this.result = trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Only a step-level {@code WORKSPACE} artifact source needs a workspace; otherwise the step also runs in
     * Declarative {@code post} outside a {@code node}.
     */
    @Override
    public boolean requiresWorkspace() {
        return artifacts != null && artifacts.isEnabled() && artifacts.getSource() == ArtifactSource.WORKSPACE;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env,
                        @NonNull Launcher launcher, @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        publish(run, workspace, env, listener);
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull EnvVars env, @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        publish(run, null, env, listener);
    }

    private void publish(Run<?, ?> run, @CheckForNull FilePath workspace, EnvVars env, TaskListener listener)
            throws InterruptedException, IOException {
        Result reported = effectiveResult(run);
        EffectiveSettings settings;
        try {
            settings = SettingsResolver.resolve(run.getParent(), this);
        } catch (AbortException e) {
            DevBuildAction.record(run, List.of());
            throw e;
        }
        try {
            PublishOutcome outcome = DevBuildNotifier.publish(run, workspace, env, listener, settings, reported);
            DevBuildAction.record(run, outcome instanceof PublishOutcome.Sent sent ? sent.messageIds() : List.of());
        } catch (AbortException e) {
            DevBuildAction.record(run, List.of());
            throw e;
        } catch (IOException e) {
            DevBuildAction.record(run, List.of());
            listener.error("[DevBuild] Discord notification failed: " + e.getMessage());
        }
    }

    private Result effectiveResult(Run<?, ?> run) throws AbortException {
        if (result != null) {
            if (!RESULTS.contains(result)) {
                throw new AbortException("[DevBuild] Unknown result '" + result + "'; use one of " + RESULTS);
            }
            return Result.fromString(result);
        }
        Result current = run.getResult();
        return current == null ? Result.SUCCESS : current;
    }

    @Extension
    @Symbol("discordDevBuild")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Post build to Discord (DevBuild)";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillWebhookNameItems(@QueryParameter String webhookName) {
            return DevBuildForms.webhookNameItems(webhookName);
        }

        public FormValidation doCheckResult(@QueryParameter String value) {
            if (TextUtils.isBlank(value) || value.contains("${")
                    || RESULTS.contains(value.trim().toUpperCase(Locale.ROOT))) {
                return FormValidation.ok();
            }
            return FormValidation.error("Use one of " + String.join(", ", RESULTS));
        }
    }
}
