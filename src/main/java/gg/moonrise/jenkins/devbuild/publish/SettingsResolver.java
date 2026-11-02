package gg.moonrise.jenkins.devbuild.publish;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.config.AutoPublishConfig;
import gg.moonrise.jenkins.devbuild.config.DevBuildConfig;
import gg.moonrise.jenkins.devbuild.config.DevBuildFolderProperty;
import gg.moonrise.jenkins.devbuild.config.DevBuildGlobalConfiguration;
import gg.moonrise.jenkins.devbuild.config.DevBuildJobProperty;
import gg.moonrise.jenkins.devbuild.webhook.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.settings.EmbedSettings;
import gg.moonrise.jenkins.devbuild.settings.MessageSettings;
import gg.moonrise.jenkins.devbuild.webhook.NamedWebhook;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import hudson.AbortException;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Applies the inheritance order: step or publisher, job property, folder properties from the nearest folder up,
 * global defaults, built-in defaults.
 */
public final class SettingsResolver {

    private SettingsResolver() {
    }

    /**
     * A job or folder property that may publish automatically, with the label of where it was found.
     */
    public record AutoPublishLevel(@NonNull String label, @NonNull AutoPublishConfig config) {
    }

    /**
     * Resolves the settings for a build of {@code job}.
     *
     * @param stepLevel the step or publisher configuration, or {@code null} when publishing automatically
     * @throws AbortException when no webhook can be resolved or a referenced named webhook does not exist
     */
    @NonNull
    public static EffectiveSettings resolve(@NonNull Job<?, ?> job, @CheckForNull DevBuildConfig stepLevel)
            throws AbortException {
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        List<ConfigLevel> levels = levels(job, stepLevel, global);
        WebhookChoice webhook = resolveWebhook(levels, global);
        return new EffectiveSettings(
                webhook.webhook(),
                webhook.origin(),
                first(levels, ConfigLevel::message, MessageSettings::new),
                first(levels, ConfigLevel::embed, EmbedSettings::new),
                first(levels, ConfigLevel::artifacts, ArtifactSettings::new),
                first(levels, ConfigLevel::conditions, NotifyConditions::new),
                Duration.ofSeconds(global.getTimeoutSeconds()),
                global.getMaxRetries());
    }

    /**
     * Lists the configuration levels for a job, highest priority first.
     */
    @NonNull
    public static List<ConfigLevel> levels(@NonNull Job<?, ?> job, @CheckForNull DevBuildConfig stepLevel,
                                           @NonNull DevBuildGlobalConfiguration global) {
        List<ConfigLevel> levels = new ArrayList<>();
        if (stepLevel != null) {
            levels.add(ConfigLevel.of("step", stepLevel));
        }
        for (AutoPublishLevel level : propertyLevels(job)) {
            levels.add(ConfigLevel.of(level.label(), level.config()));
        }
        levels.add(new ConfigLevel("global configuration", global.getDefaultWebhookName(), null,
                global.getMessage(), global.getEmbed(), global.getArtifacts(), global.getConditions()));
        return levels;
    }

    /**
     * The nearest job or folder property, which decides whether builds are published automatically.
     */
    @NonNull
    public static Optional<AutoPublishLevel> nearestPropertyLevel(@NonNull Job<?, ?> job) {
        List<AutoPublishLevel> levels = propertyLevels(job);
        return levels.isEmpty() ? Optional.empty() : Optional.of(levels.get(0));
    }

    /**
     * The job property, then properties of the enclosing items from the nearest up. Matrix and Maven module jobs
     * also see the properties of their parent project.
     */
    @NonNull
    private static List<AutoPublishLevel> propertyLevels(@NonNull Job<?, ?> job) {
        List<AutoPublishLevel> levels = new ArrayList<>();
        addJobProperty(levels, job);
        ItemGroup<?> group = job.getParent();
        while (group instanceof Item item) {
            if (group instanceof AbstractFolder<?> folder) {
                DevBuildFolderProperty property = folder.getProperties().get(DevBuildFolderProperty.class);
                if (property != null) {
                    levels.add(new AutoPublishLevel("folder '" + folder.getFullName() + "'", property));
                }
            } else if (group instanceof Job<?, ?> parentJob) {
                addJobProperty(levels, parentJob);
            }
            group = item.getParent();
        }
        return levels;
    }

    private static void addJobProperty(List<AutoPublishLevel> levels, Job<?, ?> job) {
        DevBuildJobProperty property = job.getProperty(DevBuildJobProperty.class);
        if (property != null && levels.stream().noneMatch(level -> level.config() == property)) {
            levels.add(new AutoPublishLevel("job '" + job.getFullName() + "'", property));
        }
    }

    private record WebhookChoice(DiscordWebhook webhook, String origin) {
    }

    /**
     * The first level with a webhook URL source wins. An inline webhook without a URL source (only a thread id,
     * thread name or tags) overrides the thread settings of the webhook found further down.
     */
    private static WebhookChoice resolveWebhook(List<ConfigLevel> levels, DevBuildGlobalConfiguration global)
            throws AbortException {
        DiscordWebhook threadOverride = null;
        String overrideOrigin = null;
        for (ConfigLevel level : levels) {
            DiscordWebhook inline = level.webhook();
            if (inline != null && WebhookCredentials.hasUrlSource(inline)) {
                return withOverride(inline, level.label(), threadOverride, overrideOrigin);
            }
            if (inline != null && threadOverride == null && hasThreadSettings(inline)) {
                threadOverride = inline;
                overrideOrigin = level.label();
            }
            String name = level.webhookName();
            if (name != null) {
                NamedWebhook named = global.findWebhook(name).orElseThrow(() -> new AbortException(
                        "[DevBuild] The Discord webhook '" + name + "' referenced by the " + level.label()
                                + " is not defined. Add it under Manage Jenkins > System > DevBuild Discord Publisher."));
                return withOverride(named.getWebhook(), level.label() + " (webhook '" + name + "')",
                        threadOverride, overrideOrigin);
            }
        }
        throw new AbortException("[DevBuild] No Discord webhook is configured. Set webhookName or webhook on the step, "
                + "the job property or a parent folder, or choose a default webhook in the global configuration.");
    }

    private static WebhookChoice withOverride(DiscordWebhook base, String origin,
                                              @CheckForNull DiscordWebhook override, @CheckForNull String overrideOrigin) {
        if (override == null) {
            return new WebhookChoice(base, origin);
        }
        DiscordWebhook merged = new DiscordWebhook();
        merged.setCredentialsId(base.getCredentialsId());
        merged.setWebhookUrl(base.getWebhookUrl());
        merged.setThreadId(override.getThreadId());
        merged.setThreadName(override.getThreadName());
        merged.setAppliedTags(override.getAppliedTags());
        return new WebhookChoice(merged, origin + ", thread settings from the " + overrideOrigin);
    }

    private static boolean hasThreadSettings(DiscordWebhook webhook) {
        return webhook.getThreadId() != null || webhook.getThreadName() != null || webhook.getAppliedTags() != null;
    }

    private static <T> T first(List<ConfigLevel> levels, Function<ConfigLevel, T> getter, Supplier<T> defaults) {
        for (ConfigLevel level : levels) {
            T value = getter.apply(level);
            if (value != null) {
                return value;
            }
        }
        return defaults.get();
    }
}
