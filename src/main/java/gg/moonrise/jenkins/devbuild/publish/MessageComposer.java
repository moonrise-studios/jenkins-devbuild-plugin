package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.EmbedField;
import gg.moonrise.jenkins.devbuild.EmbedSettings;
import gg.moonrise.jenkins.devbuild.FilePlacement;
import gg.moonrise.jenkins.devbuild.MessageSettings;
import gg.moonrise.jenkins.devbuild.discord.DiscordEmbed;
import gg.moonrise.jenkins.devbuild.discord.DiscordLimits;
import gg.moonrise.jenkins.devbuild.discord.WebhookMessage;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.model.Result;
import hudson.model.Run;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the webhook messages for a build: the main message with the embed, then one follow-up message per batch of
 * files. With {@link FilePlacement#WITH_EMBED}, or without an embed, the first batch goes with the main message
 * instead.
 */
public final class MessageComposer {

    private MessageComposer() {
    }

    /**
     * Everything the composer needs about the build.
     */
    public record Input(
            @NonNull Run<?, ?> run,
            @NonNull Result result,
            @NonNull EffectiveSettings settings,
            @NonNull TemplateVariables variables,
            @NonNull UploadPlan plan,
            @NonNull List<BuildFile> listed,
            @NonNull String changes) {
    }

    /**
     * Composes all messages. The first message creates the forum post when {@code threadName} is set and no
     * {@code threadId} is configured; follow-ups never carry a thread name, tags or the message content.
     */
    @NonNull
    public static List<WebhookMessage> compose(@NonNull Input input) {
        List<List<BuildFile>> batches = input.plan().batches();
        // Index of the first batch sent as a follow-up; the batches before it go with the main message.
        int firstFollowUp = batches.isEmpty() || separateFiles(input) ? 0 : 1;
        List<WebhookMessage> messages = new ArrayList<>();
        messages.add(mainMessage(input, firstFollowUp == 1 ? batches.get(0) : List.of()));
        for (int i = firstFollowUp; i < batches.size(); i++) {
            WebhookMessage followUp = baseMessage(input.settings().message(), input.variables());
            followUp.allowedMentions(List.of());
            batches.get(i).forEach(file -> followUp.attachment(file.toAttachment()));
            messages.add(followUp);
        }
        return messages;
    }

    /**
     * Whether every batch goes into follow-ups so the files show below the embed. Without an embed the files stay in
     * the main message, which would otherwise be empty.
     */
    private static boolean separateFiles(Input input) {
        EffectiveSettings settings = input.settings();
        return settings.artifacts().getPlacement() == FilePlacement.AFTER_EMBED && settings.embed().isEnabled();
    }

    private static WebhookMessage mainMessage(Input input, List<BuildFile> files) {
        EffectiveSettings settings = input.settings();
        MessageSettings messageSettings = settings.message();
        TemplateVariables variables = input.variables();
        WebhookMessage message = baseMessage(messageSettings, variables)
                .tts(messageSettings.isTts())
                .allowedMentions(messageSettings.allowedMentionTypes());
        String content = variables.expand(messageSettings.getContent());

        DiscordWebhook webhook = settings.webhook();
        if (webhook.getThreadId() == null && webhook.getThreadName() != null) {
            message.threadName(variables.expand(webhook.getThreadName()));
            message.appliedTags(webhook.appliedTagList());
        }

        EmbedSettings embedSettings = settings.embed();
        if (embedSettings.isEnabled()) {
            message.embed(buildEmbed(input).applyLimits());
        } else if (!input.plan().linked().isEmpty()) {
            content = appendLines(content, ArtifactListFormatter.linkedLines(input.plan().linked()));
        }
        if (TextUtils.isBlank(content) && !embedSettings.isEnabled() && input.plan().batches().isEmpty()) {
            content = variables.expand("${FULL_DISPLAY_NAME}: ${BUILD_RESULT}");
        }
        message.content(content);

        files.forEach(file -> message.attachment(file.toAttachment()));
        return message;
    }

    private static WebhookMessage baseMessage(MessageSettings settings, TemplateVariables variables) {
        WebhookMessage message = new WebhookMessage()
                .username(variables.expand(settings.getUsername()))
                .avatarUrl(variables.expand(settings.getAvatarUrl()));
        if (settings.isSuppressNotifications()) {
            message.flags(DiscordLimits.FLAG_SUPPRESS_NOTIFICATIONS);
        }
        return message;
    }

    private static DiscordEmbed buildEmbed(Input input) {
        EmbedSettings settings = input.settings().embed();
        TemplateVariables variables = input.variables();
        Run<?, ?> run = input.run();
        DiscordEmbed embed = new DiscordEmbed()
                .title(variables.expand(settings.getTitle()))
                .description(variables.expand(settings.getDescription()))
                .url(variables.expand(settings.getUrl()))
                .color(settings.colorFor(input.result()))
                .author(variables.expand(settings.getAuthorName()), variables.expand(settings.getAuthorUrl()),
                        variables.expand(settings.getAuthorIconUrl()))
                .footer(variables.expand(settings.getFooterText()), variables.expand(settings.getFooterIconUrl()))
                .thumbnail(variables.expand(settings.getThumbnailUrl()))
                .image(variables.expand(settings.getImageUrl()));
        if (settings.isTimestamp()) {
            long end = run.getStartTimeInMillis() + TemplateVariables.durationMillis(run);
            embed.timestamp(Instant.ofEpochMilli(end).toString());
        }
        if (settings.isShowResult()) {
            embed.field("Result", resultLabel(input.result()), true);
        }
        if (settings.isShowDuration()) {
            embed.field("Duration", TextUtils.nullToEmpty(variables.get("BUILD_DURATION")), true);
        }
        String branch = variables.get("BRANCH");
        if (settings.isShowBranch() && !TextUtils.isBlank(branch)) {
            embed.field("Branch", "`" + branch.replace("`", "") + "`", true);
        }
        String commit = variables.get("COMMIT_SHORT");
        if (settings.isShowCommit() && !TextUtils.isBlank(commit)) {
            embed.field("Commit", "`" + commit.replace("`", "") + "`", true);
        }
        if (settings.isShowChanges() && !input.changes().isEmpty()) {
            embed.field("Changes", input.changes(), false);
        }
        if (settings.isShowArtifacts() && !input.listed().isEmpty()) {
            Set<BuildFile> linked = Set.copyOf(input.plan().linked());
            embed.field("Artifacts", ArtifactListFormatter.fieldValue(input.listed(), linked, DiscordLimits.FIELD_VALUE), false);
        }
        for (EmbedField field : settings.getFields()) {
            embed.field(variables.expand(field.getName()), variables.expand(field.getValue()), field.isInline());
        }
        return embed;
    }

    private static String resultLabel(Result result) {
        String name = result.toString().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String appendLines(String content, List<String> lines) {
        String joined = String.join("\n", lines);
        return TextUtils.isBlank(content) ? joined : content + "\n" + joined;
    }
}
