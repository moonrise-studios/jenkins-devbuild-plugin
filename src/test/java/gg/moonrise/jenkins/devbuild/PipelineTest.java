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
                      discordDevBuild webhookName: 'devbuilds', artifacts: [includes: 'build/libs/*.jar'],
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
            assertEquals("outside app", outside.payload().getString("content"));
            assertEquals("app #1", outside.embed().getString("title"));
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
            String artifacts = request.field("Artifacts").getString("value");
            assertTrue(artifacts.startsWith("plugin.jar · 14 B"), artifacts);
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
            assertEquals("SUCCESS", request.payload().getString("content"));
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
                    discordDevBuild webhookName: 'devbuilds', artifacts: [maxFiles: 15]
                    """);

            r.buildAndAssertSuccess(job);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            FakeDiscord.Request first = requests.get(0);
            JSONObject payload = first.payload();
            assertEquals("Build 1 of many", payload.getString("thread_name"));
            assertEquals(List.of("111111111111111111", "222222222222222222"), payload.getJSONArray("applied_tags"));
            assertFalse(first.query().containsKey("thread_id"));
            assertEquals(10, first.files().size());
            assertEquals("mod-10.jar", first.files().get(0).filename());

            FakeDiscord.Request followUp = requests.get(1);
            assertEquals(FakeDiscord.NEW_THREAD_ID, followUp.query().get("thread_id"));
            assertFalse(followUp.payload().has("thread_name"));
            assertFalse(followUp.payload().has("embeds"));
            assertEquals(List.of("mod-20.jar", "mod-21.jar"),
                    followUp.files().stream().map(FakeDiscord.Part::filename).toList());
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
                      discordDevBuild webhookName: 'devbuilds',
                          message: [content: '<@&42> ${PROJECT_NAME}|${BUILD_DISPLAY_NAME}|${ARTIFACTS}|${COMMIT_SHORT}|[${BRANCH}]|${UNKNOWN}',
                                    username: 'Builds for ${JOB_NAME}', mentionRoles: false, suppressNotifications: true],
                          embed: [description: 'Version $VERSION', footerText: '${FULL_DISPLAY_NAME}',
                                  fields: [[name: 'Version', value: '${VERSION}', inline: true]]]
                    }
                    """);

            r.buildAndAssertSuccess(job);

            FakeDiscord.Request request = discord.only();
            JSONObject payload = request.payload();
            assertEquals("<@&42> tpl|#1|a.jar|0123456|[]|${UNKNOWN}", payload.getString("content"));
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

            JSONObject payload = discord.only().payload();
            assertEquals(2000, payload.getString("content").length());
            JSONObject embed = payload.getJSONArray("embeds").getJSONObject(0);
            assertEquals(256, embed.getString("title").length());
            assertTrue(embed.getString("title").endsWith("…"));
            int total = embed.getString("title").length() + embed.optString("description").length();
            for (Object field : embed.getJSONArray("fields")) {
                JSONObject f = (JSONObject) field;
                total += f.getString("name").length() + f.getString("value").length();
            }
            assertTrue(total <= 6000, "embed total " + total);
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
            assertEquals(0x95A5A6, request.embed().getInt("color"));
            assertEquals("Aborted", request.field("Result").getString("value"));
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
            assertEquals("Success", discord.requests().get(1).field("Result").getString("value"));
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
            assertEquals("`release/1.0`", discord.requests().get(1).field("Branch").getString("value"));
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
            assertEquals("Dev build 1", discord.only().embed().getString("title"));
        }

        WorkflowJob job = TestSupport.pipeline(r, "syntax", "echo 'hi'");
        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            String page = wc.getPage(job, "pipeline-syntax").getWebResponse().getContentAsString();
            assertTrue(page.contains("discordDevBuild"), "step missing from the Snippet Generator");
        }
    }
}
