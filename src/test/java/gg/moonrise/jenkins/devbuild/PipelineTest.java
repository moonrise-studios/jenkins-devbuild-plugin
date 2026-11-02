package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import java.util.List;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PipelineTest {

    @Test
    void stepWithMapArgumentsInsideAndOutsideNode(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "app", """
                    node {
                      writeFile file: 'build/libs/app.jar', text: 'inside'
                      writeFile file: 'build/libs/app-javadoc.jar', text: 'docs'
                      archiveArtifacts 'build/libs/*.jar'
                      discordDevBuild webhookName: 'devbuilds',
                          artifacts: [includes: 'build/libs/*.jar', placement: 'WITH_EMBED'],
                          embed: [title: 'Dev build ${BUILD_NUMBER}']
                    }
                    discordDevBuild webhookName: 'devbuilds', message: [content: 'outside ${JOB_DISPLAY_NAME}']
                    """);

            WorkflowRun run = r.buildAndAssertSuccess(job);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            FakeDiscord.Request inside = requests.get(0);
            assertEquals("Dev build 1", inside.embed().getString("title"));
            assertEquals(1, inside.files().size());
            assertEquals("app.jar", inside.files().get(0).filename());
            assertEquals("inside", inside.files().get(0).text());

            FakeDiscord.Request outside = requests.get(1);
            assertEquals("outside app", outside.messageText());
            assertEquals("app #1", outside.cardTitle());
            assertEquals(List.of("app.jar"), outside.cardFiles());
            assertEquals("app.jar", outside.files().get(0).filename());
            assertEquals(2, run.getAction(DevBuildAction.class).getInvocations());
        }
    }

    @Test
    void workspaceSourceUploadsUnarchivedFiles(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "ws", """
                    node {
                      writeFile file: 'target/plugin.jar', text: 'from workspace'
                      discordDevBuild webhookName: 'devbuilds', artifacts: [source: 'WORKSPACE']
                    }
                    """);

            r.buildAndAssertSuccess(job);

            FakeDiscord.Request request = discord.only();
            assertEquals("plugin.jar", request.files().get(0).filename());
            assertEquals("from workspace", request.files().get(0).text());
            String text = request.cardText();
            assertFalse(text.contains("**Artifacts**"), text);
            assertEquals(List.of("plugin.jar"), request.cardFiles());
        }
    }

    @Test
    void declarativePostOutsideNode(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "decl", """
                    pipeline {
                      agent none
                      stages {
                        stage('Build') {
                          agent any
                          steps {
                            writeFile file: 'out/decl.jar', text: 'declarative'
                            archiveArtifacts 'out/*.jar'
                          }
                        }
                      }
                      post {
                        success {
                          discordDevBuild webhookName: 'devbuilds', message: [content: '${BUILD_RESULT}']
                        }
                      }
                    }
                    """);

            r.buildAndAssertSuccess(job);

            FakeDiscord.Request request = discord.only();
            assertEquals("SUCCESS", request.messageText());
            assertEquals("decl.jar", request.files().get(0).filename());
            assertEquals("declarative", request.files().get(0).text());
        }
    }

    @Test
    void declarativePostInsideAgent(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "decl-agent", """
                    pipeline {
                      agent any
                      stages {
                        stage('Build') {
                          steps {
                            writeFile file: 'out/a.jar', text: 'a'
                          }
                        }
                      }
                      post {
                        always {
                          discordDevBuild webhookName: 'devbuilds'
                        }
                      }
                    }
                    """);

            r.buildAndAssertSuccess(job);

            assertEquals("a.jar", discord.only().files().get(0).filename());
        }
    }

    @Test
    void forumPostCardHoldsTheFirstTenFilesAndTheRestFollow(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            DiscordWebhook webhook = TestSupport.registerWebhook(discord);
            webhook.setThreadName("Build ${BUILD_NUMBER} of ${PROJECT_NAME}");
            webhook.setAppliedTags("111111111111111111");
            DevBuildGlobalConfiguration.get().save();
            WorkflowJob job = TestSupport.pipeline(r, "many", """
                    node {
                      for (int i = 10; i < 22; i++) {
                        writeFile file: "libs/mod-${i}.jar", text: "jar ${i}"
                      }
                      archiveArtifacts 'libs/*.jar'
                    }
                    discordDevBuild webhookName: 'devbuilds', artifacts: [maxFiles: 15],
                        message: [content: '<@&123456789012345678> new build', username: 'Builds',
                                  avatarUrl: 'https://example.com/avatar.png', suppressNotifications: true, tts: true]
                    """);

            r.buildAndAssertSuccess(job);

            // IN_EMBED (default): the card creates the forum post with the first 10 files inside, then 2 follow.
            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            FakeDiscord.Request first = requests.get(0);
            JSONObject payload = first.payload();
            assertEquals("true", first.query().get("with_components"));
            assertFalse(first.query().containsKey("thread_id"));
            assertEquals(FakeDiscord.COMPONENTS_V2_FLAG | 4096, payload.getInt("flags"));
            assertFalse(payload.has("content"));
            assertFalse(payload.has("embeds"));
            assertFalse(payload.has("tts"), "TTS is not available for a Components V2 message");
            assertEquals("Build 1 of many", payload.getString("thread_name"));
            assertEquals(List.of("111111111111111111"), payload.getJSONArray("applied_tags"));
            assertEquals("Builds", payload.getString("username"));
            assertEquals(List.of("users", "roles"), payload.getJSONObject("allowed_mentions").getJSONArray("parse"));
            assertEquals("<@&123456789012345678> new build", first.messageText());
            assertEquals("many #1", first.cardTitle());
            List<String> firstBatch = List.of("mod-10.jar", "mod-11.jar", "mod-12.jar", "mod-13.jar", "mod-14.jar",
                    "mod-15.jar", "mod-16.jar", "mod-17.jar", "mod-18.jar", "mod-19.jar");
            assertEquals(firstBatch, first.cardFiles());
            assertEquals(firstBatch, first.files().stream().map(FakeDiscord.Part::filename).toList());
            List<String> attachmentNames = payload.getJSONArray("attachments").stream()
                    .map(a -> ((JSONObject) a).getString("filename")).toList();
            assertEquals(firstBatch, attachmentNames);

            FakeDiscord.Request followUp = requests.get(1);
            JSONObject followUpPayload = followUp.payload();
            assertEquals(FakeDiscord.NEW_THREAD_ID, followUp.query().get("thread_id"));
            assertFalse(followUp.query().containsKey("with_components"));
            assertFalse(followUpPayload.has("components"));
            assertFalse(followUpPayload.has("thread_name"));
            assertFalse(followUpPayload.has("content"));
            assertEquals(4096, followUpPayload.getInt("flags"));
            assertEquals(List.of(), followUpPayload.getJSONObject("allowed_mentions").getJSONArray("parse"));
            assertEquals("Builds", followUpPayload.getString("username"));
            assertEquals("https://example.com/avatar.png", followUpPayload.getString("avatar_url"));
            assertEquals(List.of("mod-20.jar", "mod-21.jar"), followUp.files().stream().map(FakeDiscord.Part::filename).toList());
        }
    }

    @Test
    void forumPostAndFollowUpMessagesForManyFiles(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            DiscordWebhook webhook = TestSupport.registerWebhook(discord);
            webhook.setThreadName("Build ${BUILD_NUMBER} of ${PROJECT_NAME}");
            webhook.setAppliedTags("111111111111111111, 222222222222222222");
            DevBuildGlobalConfiguration.get().save();
            WorkflowJob job = TestSupport.pipeline(r, "many", """
                    node {
                      for (int i = 10; i < 22; i++) {
                        writeFile file: "libs/mod-${i}.jar", text: "jar ${i}"
                      }
                      archiveArtifacts 'libs/*.jar'
                    }
                    discordDevBuild webhookName: 'devbuilds', artifacts: [maxFiles: 15, placement: 'AFTER_EMBED'],
                        message: [content: '<@&123456789012345678> new build', username: 'Builds',
                                  avatarUrl: 'https://example.com/avatar.png']
                    """);

            r.buildAndAssertSuccess(job);

            // AFTER_EMBED: the embed creates the forum post, then 10 + 2 files follow in that post.
            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(3, requests.size());
            FakeDiscord.Request first = requests.get(0);
            JSONObject payload = first.payload();
            assertEquals("Build 1 of many", payload.getString("thread_name"));
            assertEquals(List.of("111111111111111111", "222222222222222222"), payload.getJSONArray("applied_tags"));
            assertEquals("<@&123456789012345678> new build", payload.getString("content"));
            assertEquals("many #1", first.embed().getString("title"));
            assertFalse(first.query().containsKey("thread_id"));
            assertEquals("true", first.query().get("wait"));
            assertTrue(first.files().isEmpty());
            assertFalse(payload.has("attachments"));

            List<List<String>> batches = List.of(
                    List.of("mod-10.jar", "mod-11.jar", "mod-12.jar", "mod-13.jar", "mod-14.jar",
                            "mod-15.jar", "mod-16.jar", "mod-17.jar", "mod-18.jar", "mod-19.jar"),
                    List.of("mod-20.jar", "mod-21.jar"));
            for (int i = 0; i < batches.size(); i++) {
                FakeDiscord.Request followUp = requests.get(i + 1);
                JSONObject followUpPayload = followUp.payload();
                assertEquals(FakeDiscord.NEW_THREAD_ID, followUp.query().get("thread_id"));
                assertFalse(followUpPayload.has("thread_name"));
                assertFalse(followUpPayload.has("applied_tags"));
                assertFalse(followUpPayload.has("embeds"));
                assertFalse(followUpPayload.has("content"));
                assertEquals("Builds", followUpPayload.getString("username"));
                assertEquals("https://example.com/avatar.png", followUpPayload.getString("avatar_url"));
                assertEquals(batches.get(i), followUp.files().stream().map(FakeDiscord.Part::filename).toList());
            }
        }
    }

    @Test
    void filesFollowTheEmbedIntoTheConfiguredThread(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "thread-files", """
                    node {
                      writeFile file: 'build/libs/app.jar', text: 'app'
                      archiveArtifacts 'build/libs/*.jar'
                    }
                    discordDevBuild webhookName: 'devbuilds', webhook: [threadId: '1234567890123456789'],
                        artifacts: [placement: 'AFTER_EMBED']
                    """);

            r.buildAndAssertSuccess(job);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            assertTrue(requests.get(0).payload().has("embeds"));
            assertTrue(requests.get(0).files().isEmpty());
            assertEquals("app.jar", requests.get(1).files().get(0).filename());
            for (FakeDiscord.Request request : requests) {
                assertEquals("1234567890123456789", request.query().get("thread_id"));
                assertFalse(request.payload().has("thread_name"));
            }
        }
    }

    @Test
    void filesAreNotSentWhenTheEmbedMessageFails(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            discord.enqueue(400, "{\"message\":\"Invalid Form Body\",\"code\":50035}");
            WorkflowJob job = TestSupport.pipeline(r, "first-fails", """
                    node {
                      writeFile file: 'build/libs/app.jar', text: 'app'
                      archiveArtifacts 'build/libs/*.jar'
                    }
                    discordDevBuild webhookName: 'devbuilds', artifacts: [placement: 'AFTER_EMBED']
                    """);

            WorkflowRun run = r.buildAndAssertSuccess(job);

            assertEquals(1, discord.requests().size(), "no follow-up after the embed message failed");
            assertTrue(discord.only().files().isEmpty());
            r.assertLogContains("Discord notification failed", run);
        }
    }

    @Test
    void failedFilesMessageIsLogged(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            discord.enqueue(200, "{\"id\":\"900000000000000001\",\"channel_id\":\"" + FakeDiscord.CHANNEL_ID + "\"}");
            discord.enqueue(413, "{\"message\":\"Request entity too large\",\"code\":40005}");
            WorkflowJob job = TestSupport.pipeline(r, "follow-up-fails", """
                    node {
                      writeFile file: 'build/libs/app.jar', text: 'app'
                      archiveArtifacts 'build/libs/*.jar'
                    }
                    discordDevBuild webhookName: 'devbuilds', artifacts: [placement: 'AFTER_EMBED']
                    """);

            WorkflowRun run = r.buildAndAssertSuccess(job);

            assertEquals(2, discord.requests().size());
            assertEquals("app.jar", discord.requests().get(1).files().get(0).filename());
            r.assertLogContains("Discord notification failed", run);
        }
    }

    @Test
    void blankThumbnailIsOmitted(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "no-thumb", """
                    discordDevBuild webhookName: 'devbuilds'
                    discordDevBuild webhookName: 'devbuilds', embed: [thumbnailUrl: '']
                    """);

            r.buildAndAssertSuccess(job);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            assertEquals(EmbedSettings.DEFAULT_THUMBNAIL_URL, requests.get(0).cardThumbnail());
            assertNull(requests.get(1).cardThumbnail());
            assertEquals("no-thumb #1", requests.get(1).cardTitle());
        }
    }

    @Test
    void threadIdIsSentAsQueryParameter(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "thread", """
                    discordDevBuild webhookName: 'devbuilds', webhook: [threadId: '1234567890123456789', threadName: 'ignored']
                    """);

            r.buildAndAssertSuccess(job);

            FakeDiscord.Request request = discord.only();
            assertEquals("1234567890123456789", request.query().get("thread_id"));
            assertFalse(request.payload().has("thread_name"));
        }
    }

    @Test
    void templatesExpandBuildVariables(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "tpl", """
                    node {
                      writeFile file: 'a.jar', text: 'a'
                      archiveArtifacts 'a.jar'
                    }
                    withEnv(['GIT_COMMIT=0123456789abcdef0123', 'VERSION=2.0']) {
                      discordDevBuild webhookName: 'devbuilds', artifacts: [placement: 'AFTER_EMBED'],
                          message: [content: '<@&42> ${PROJECT_NAME}|${BUILD_DISPLAY_NAME}|${ARTIFACTS}|${COMMIT_SHORT}|[${BRANCH}]|${UNKNOWN}',
                                    username: 'Builds for ${JOB_NAME}', mentionRoles: false, suppressNotifications: true],
                          embed: [description: 'Version $VERSION', footerText: '${FULL_DISPLAY_NAME}',
                                  fields: [[name: 'Version', value: '${VERSION}', inline: true]]]
                    }
                    """);

            r.buildAndAssertSuccess(job);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            FakeDiscord.Request request = requests.get(0);
            JSONObject payload = request.payload();
            assertEquals("<@&42> tpl|#1|a.jar|0123456|[]|${UNKNOWN}", payload.getString("content"));
            JSONObject filesPayload = requests.get(1).payload();
            assertFalse(filesPayload.has("content"));
            assertEquals("Builds for tpl", filesPayload.getString("username"));
            assertEquals(4096, filesPayload.getInt("flags"));
            assertEquals(List.of(), filesPayload.getJSONObject("allowed_mentions").getJSONArray("parse"));
            assertEquals("Builds for tpl", payload.getString("username"));
            assertEquals(List.of("users"), payload.getJSONObject("allowed_mentions").getJSONArray("parse"));
            assertEquals(4096, payload.getInt("flags"));
            JSONObject embed = request.embed();
            assertEquals("Version 2.0", embed.getString("description"));
            assertEquals("tpl #1", embed.getJSONObject("footer").getString("text"));
            assertEquals("`0123456`", request.field("Commit").getString("value"));
            assertEquals("2.0", request.field("Version").getString("value"));
            assertTrue(request.field("Version").getBoolean("inline"));
            assertNull(request.field("Branch"));
        }
    }

    @Test
    void longTemplatesAreTruncated(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "long", """
                    def text = 'x' * 7000
                    discordDevBuild webhookName: 'devbuilds', message: [content: text], embed: [title: text, description: text]
                    """);

            r.buildAndAssertSuccess(job);

            FakeDiscord.Request request = discord.only();
            assertEquals(2000, request.messageText().length());
            assertEquals(256, request.cardTitle().length());
            assertTrue(request.cardTitle().endsWith("…"));
            List<String> texts = request.cardTexts();
            int total = request.messageText().length() + texts.stream().mapToInt(String::length).sum();
            assertTrue(total <= 4000, "component text total " + total);
            assertTrue(texts.stream().anyMatch(t -> t.startsWith("xxx") && t.endsWith("…")),
                    "the description is shortened to fit");
        }
    }

    @Test
    void abortedBuildsAreSkippedByDefault(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "aborted", """
                    discordDevBuild webhookName: 'devbuilds', result: 'ABORTED'
                    discordDevBuild webhookName: 'devbuilds', result: 'aborted', conditions: [notifyOnAborted: true]
                    """);

            WorkflowRun run = r.buildAndAssertSuccess(job);

            r.assertLogContains("Not posting to Discord: notifyOnAborted is off", run);
            FakeDiscord.Request request = discord.only();
            assertEquals(0x95A5A6, request.card().getInt("accent_color"));
            assertTrue(request.cardText().contains("**Result:** Aborted"), request.cardText());
        }
    }

    @Test
    void backToNormalIsPostedWhenSuccessIsOff(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "normal", """
                    if (currentBuild.number == 1) {
                      currentBuild.result = 'FAILURE'
                    }
                    discordDevBuild webhookName: 'devbuilds', conditions: [notifyOnSuccess: false]
                    """);

            r.buildAndAssertStatus(Result.FAILURE, job);
            assertEquals(1, discord.requests().size());
            r.buildAndAssertSuccess(job);
            assertEquals(2, discord.requests().size(), "back to normal must be posted");
            assertTrue(discord.requests().get(1).cardText().contains("**Result:** Success"));
            WorkflowRun third = r.buildAndAssertSuccess(job);
            assertEquals(2, discord.requests().size(), "plain success must not be posted");
            r.assertLogContains("notifyOnSuccess is off", third);
        }
    }

    @Test
    void branchPatternIsIgnoredWithoutBranchName(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "branches", """
                    discordDevBuild webhookName: 'devbuilds', conditions: [branchPattern: 'main']
                    withEnv(['BRANCH_NAME=feature/x']) {
                      discordDevBuild webhookName: 'devbuilds', conditions: [branchPattern: 'main|release/.*']
                    }
                    withEnv(['BRANCH_NAME=release/1.0']) {
                      discordDevBuild webhookName: 'devbuilds', conditions: [branchPattern: 'main|release/.*']
                    }
                    """);

            WorkflowRun run = r.buildAndAssertSuccess(job);

            assertEquals(2, discord.requests().size());
            r.assertLogContains("branch 'feature/x' does not match branchPattern", run);
            assertTrue(discord.requests().get(1).cardText().contains("**Branch:** `release/1.0`"));
        }
    }

    @Test
    void discordErrorsAreLoggedWithoutFailingTheBuild(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            discord.enqueue(404, "{\"message\":\"Unknown Webhook\",\"code\":10015}");
            WorkflowJob job = TestSupport.pipeline(r, "gone", "discordDevBuild webhookName: 'devbuilds'");

            WorkflowRun run = r.buildAndAssertSuccess(job);

            r.assertLogContains("Discord notification failed", run);
            r.assertLogContains("webhook was probably deleted", run);
            r.assertLogNotContains(FakeDiscord.TOKEN, run);
        }
    }

    @Test
    void unknownWebhookNameFailsTheStep(JenkinsRule r) throws Exception {
        WorkflowJob job = TestSupport.pipeline(r, "unknown", "discordDevBuild webhookName: 'nope'");
        WorkflowRun run = r.buildAndAssertStatus(Result.FAILURE, job);
        r.assertLogContains("The Discord webhook 'nope' referenced by the step is not defined", run);
    }

    @Test
    void snippetGeneratorRoundTrip(JenkinsRule r) throws Exception {
        DevBuildPublisher publisher = new DevBuildPublisher();
        publisher.setWebhookName("devbuilds");
        ArtifactSettings artifacts = new ArtifactSettings();
        artifacts.setIncludes("build/libs/*.jar");
        publisher.setArtifacts(artifacts);
        EmbedSettings embed = new EmbedSettings();
        embed.setTitle("Dev build ${BUILD_NUMBER}");
        publisher.setEmbed(embed);

        String snippet = Snippetizer.object2Groovy(new CoreStep(publisher));
        assertEquals("discordDevBuild artifacts: devBuildArtifacts(includes: 'build/libs/*.jar'), "
                + "embed: devBuildEmbed(title: 'Dev build ${BUILD_NUMBER}'), webhookName: 'devbuilds'", snippet);

        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "snippet", snippet);
            r.buildAndAssertSuccess(job);
            assertEquals("Dev build 1", discord.only().cardTitle());
        }

        WorkflowJob job = TestSupport.pipeline(r, "syntax", "echo 'hi'");
        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            String page = wc.getPage(job, "pipeline-syntax").getWebResponse().getContentAsString();
            assertTrue(page.contains("discordDevBuild"), "step missing from the Snippet Generator");
        }
    }
}
