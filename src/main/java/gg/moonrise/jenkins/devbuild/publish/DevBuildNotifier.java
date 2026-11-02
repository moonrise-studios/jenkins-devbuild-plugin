package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.discord.DiscordClient;
import gg.moonrise.jenkins.devbuild.discord.DiscordResponse;
import gg.moonrise.jenkins.devbuild.discord.WebhookMessage;
import gg.moonrise.jenkins.devbuild.discord.WebhookUrls;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes one build to Discord: checks the notify conditions, collects and plans the files, renders the messages
 * and sends them.
 */
public final class DevBuildNotifier {

    private DevBuildNotifier() {
    }

    /**
     * Publishes a build.
     *
     * @param workspace the workspace, or {@code null} when none is available
     * @param result    the result to report
     * @throws hudson.AbortException for configuration problems (missing credentials, oversize policy FAIL)
     * @throws IOException           when Discord cannot be reached or rejects the request
     */
    @NonNull
    public static PublishOutcome publish(@NonNull Run<?, ?> run, @CheckForNull FilePath workspace, @NonNull EnvVars env,
                                         @NonNull TaskListener listener, @NonNull EffectiveSettings settings,
                                         @NonNull Result result) throws IOException, InterruptedException {
        PrintStream log = listener.getLogger();
        Run<?, ?> previous = run.getPreviousCompletedBuild();
        NotifyDecision decision = NotifyDecision.evaluate(settings.conditions(), result,
                previous == null ? null : previous.getResult(), env.get("BRANCH_NAME"));
        if (decision instanceof NotifyDecision.Skip skip) {
            log.println("[DevBuild] Not posting to Discord: " + skip.reason());
            return new PublishOutcome.Skipped(skip.reason());
        }

        String webhookUrl = WebhookCredentials.resolveUrl(settings.webhook(), run);
        ArtifactSettings artifacts = settings.artifacts();
        List<BuildFile> files = artifacts.isEnabled()
                ? ArtifactCollector.collect(run, workspace, artifacts, log)
                : List.of();
        UploadPlan plan;
        if (artifacts.attachesFor(result)) {
            plan = UploadPlanner.plan(files, artifacts, log);
        } else {
            if (!files.isEmpty()) {
                log.println("[DevBuild] Not uploading files for a " + result + " build (onlyOnSuccess)");
            }
            plan = UploadPlan.listOnly(files);
        }
        List<BuildFile> listed = new ArrayList<>(plan.attached());
        listed.addAll(plan.linked());

        String changes = ChangeLogFormatter.format(run, settings.embed().getMaxChanges());
        TemplateVariables variables = TemplateVariables.of(run, env, result, plan.attached(), changes);
        List<WebhookMessage> messages = MessageComposer.compose(
                new MessageComposer.Input(run, result, settings, variables, plan, listed, changes));

        log.printf("[DevBuild] Posting %s to Discord webhook %s (%s), %d file(s) in %d message(s)%n",
                result, WebhookUrls.mask(webhookUrl), settings.webhookOrigin(), plan.attached().size(), messages.size());
        List<String> ids = send(webhookUrl, settings, messages, log);
        return new PublishOutcome.Sent(ids, plan.attached().size());
    }

    private static List<String> send(String webhookUrl, EffectiveSettings settings, List<WebhookMessage> messages,
                                     PrintStream log) throws IOException, InterruptedException {
        List<String> ids = new ArrayList<>();
        String threadId = settings.webhook().getThreadId();
        try (DiscordClient client = new DiscordClient(settings.timeout(), settings.maxRetries(), log)) {
            for (int i = 0; i < messages.size(); i++) {
                WebhookMessage message = messages.get(i);
                DiscordResponse response = client.execute(webhookUrl, threadId, message);
                if (response.messageId() != null) {
                    ids.add(response.messageId());
                }
                if (i == 0 && threadId == null && message.getThreadName() != null) {
                    // The first message created a forum post; follow-ups go into that new thread.
                    threadId = response.channelId();
                }
            }
        }
        log.println("[DevBuild] Posted " + ids.size() + " Discord message(s)");
        return ids;
    }
}
