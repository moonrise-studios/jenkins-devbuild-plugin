package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.ArtifactArchiver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class FreestyleTest {

    /** Writes files of the given sizes (or text) into the workspace. */
    private static final class WriteFiles extends TestBuilder {
        private final Map<String, byte[]> files;

        private WriteFiles(Map<String, byte[]> files) {
            this.files = files;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws IOException, InterruptedException {
            FilePath workspace = build.getWorkspace();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                FilePath target = workspace.child(file.getKey());
                target.getParent().mkdirs();
                target.copyFrom(new ByteArrayInputStream(file.getValue()));
            }
            return true;
        }
    }

    private static byte[] bytes(int size) {
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = (byte) (i * 31);
        }
        return content;
    }

    private static FreeStyleProject project(JenkinsRule r, Map<String, byte[]> files, DevBuildPublisher publisher)
            throws IOException {
        FreeStyleProject p = r.createFreeStyleProject("app");
        p.getBuildersList().add(new WriteFiles(files));
        p.getPublishersList().add(new ArtifactArchiver("build/libs/*.jar"));
        p.getPublishersList().add(publisher);
        return p;
    }

    @Test
    void postsEmbedAndUploadsArchivedJar(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            byte[] jar = "jar-content".getBytes(StandardCharsets.UTF_8);
            FreeStyleProject p = project(r, Map.of(
                    "build/libs/app-1.0.jar", jar,
                    "build/libs/app-1.0-sources.jar", "sources".getBytes(StandardCharsets.UTF_8)), publisher);

            FreeStyleBuild build = r.buildAndAssertSuccess(p);

            FakeDiscord.Request request = discord.only();
            assertTrue(request.isMultipart());
            JSONObject embed = request.embed();
            assertEquals("app #1", embed.getString("title"));
            assertEquals(0x2ECC71, embed.getInt("color"));
            assertEquals(r.getURL() + "job/app/1/", embed.getString("url"));
            assertTrue(embed.has("timestamp"));
            assertEquals("Success", request.field("Result").getString("value"));
            assertNotNull(request.field("Duration"));
            String artifacts = request.field("Artifacts").getString("value");
            assertTrue(artifacts.startsWith("[app-1.0.jar](" + r.getURL() + "job/app/1/artifact/build/libs/app-1.0.jar)"),
                    artifacts);
            assertFalse(artifacts.contains("sources"), artifacts);

            List<FakeDiscord.Part> files = request.files();
            assertEquals(1, files.size());
            assertEquals("app-1.0.jar", files.get(0).filename());
            assertArrayEquals(jar, files.get(0).content());
            JSONArray attachments = request.payload().getJSONArray("attachments");
            assertEquals(1, attachments.size());
            assertEquals(0, attachments.getJSONObject(0).getInt("id"));
            assertEquals("app-1.0.jar", attachments.getJSONObject(0).getString("filename"));

            DevBuildAction action = build.getAction(DevBuildAction.class);
            assertNotNull(action);
            assertEquals(1, action.getMessageIds().size());
            String log = JenkinsRule.getLog(build);
            assertFalse(log.contains(FakeDiscord.TOKEN), "token leaked into the build log");
            assertTrue(log.contains("/api/webhooks/1234…/****"), log);
        }
    }

    @Test
    void failedBuildIsPostedWithoutFiles(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            FreeStyleProject p = project(r, Map.of("build/libs/app.jar", bytes(10)), publisher);
            p.getBuildersList().add(new FailureBuilder());

            r.buildAndAssertStatus(Result.FAILURE, p);

            FakeDiscord.Request request = discord.only();
            assertFalse(request.isMultipart());
            assertEquals(0xE74C3C, request.embed().getInt("color"));
            assertEquals("Failure", request.field("Result").getString("value"));
            assertTrue(request.field("Artifacts").getString("value").contains("(not attached)"));
        }
    }

    @Test
    void splitsUploadsByBudgetAndLinksOversizeFiles(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            ArtifactSettings artifacts = new ArtifactSettings();
            artifacts.setMaxUploadSizeMb(1);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            publisher.setArtifacts(artifacts);
            int part = 600 * 1024;
            FreeStyleProject p = project(r, Map.of(
                    "build/libs/a.jar", bytes(part),
                    "build/libs/b.jar", bytes(part),
                    "build/libs/c.jar", bytes(part),
                    "build/libs/huge.jar", bytes(1536 * 1024)), publisher);

            r.buildAndAssertSuccess(p);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(3, requests.size());
            assertEquals(List.of("a.jar"), requests.get(0).files().stream().map(FakeDiscord.Part::filename).toList());
            assertEquals(List.of("b.jar"), requests.get(1).files().stream().map(FakeDiscord.Part::filename).toList());
            assertEquals(List.of("c.jar"), requests.get(2).files().stream().map(FakeDiscord.Part::filename).toList());
            assertTrue(requests.get(0).payload().has("embeds"));
            assertFalse(requests.get(1).payload().has("embeds"));
            assertEquals(1, requests.get(1).payload().getJSONArray("attachments").size());
            String artifactsField = requests.get(0).field("Artifacts").getString("value");
            assertTrue(artifactsField.contains("[huge.jar](" + r.getURL() + "job/app/1/artifact/build/libs/huge.jar)"
                    + " · 1.5 MiB (not attached)"), artifactsField);
            for (FakeDiscord.Request request : requests) {
                long total = request.files().stream().mapToLong(f -> f.content().length).sum();
                assertTrue(total <= 1024 * 1024, "batch over budget: " + total);
            }
        }
    }

    @Test
    void oversizeSkipAndFailPolicies(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            ArtifactSettings artifacts = new ArtifactSettings();
            artifacts.setMaxUploadSizeMb(1);
            artifacts.setOversizePolicy(OversizePolicy.FAIL);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            publisher.setArtifacts(artifacts);
            FreeStyleProject p = project(r, Map.of("build/libs/huge.jar", bytes(1536 * 1024)), publisher);

            FreeStyleBuild failed = r.buildAndAssertStatus(Result.FAILURE, p);
            r.assertLogContains("is larger than the upload budget of 1 MiB", failed);
            assertTrue(discord.requests().isEmpty());

            artifacts.setOversizePolicy(OversizePolicy.SKIP);
            r.buildAndAssertSuccess(p);
            FakeDiscord.Request request = discord.only();
            assertFalse(request.isMultipart());
            assertNull(request.field("Artifacts"));
        }
    }

    @Test
    void missingWebhookFailsWithClearMessage(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getPublishersList().add(new DevBuildPublisher());
        FreeStyleBuild build = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("No Discord webhook is configured", build);
    }
}
