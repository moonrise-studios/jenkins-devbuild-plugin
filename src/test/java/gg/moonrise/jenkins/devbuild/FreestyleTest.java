package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void postsOneCardWithTheArchivedJarInside(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            byte[] jar = "jar-content".getBytes(StandardCharsets.UTF_8);
            FreeStyleProject p = project(r, Map.of(
                    "build/libs/app-1.0.jar", jar,
                    "build/libs/app-1.0-sources.jar", "sources".getBytes(StandardCharsets.UTF_8)), publisher);

            FreeStyleBuild build = r.buildAndAssertSuccess(p);

            // Default placement IN_EMBED: one Components V2 message with the file inside the card.
            FakeDiscord.Request request = discord.only();
            assertTrue(request.isMultipart());
            assertEquals("true", request.query().get("with_components"));
            JSONObject payload = request.payload();
            assertTrue(request.isComponentsV2());
            assertFalse(payload.has("embeds"));
            assertFalse(payload.has("content"));
            assertFalse(payload.has("tts"));
            JSONObject card = request.card();
            assertEquals(0x2ECC71, card.getInt("accent_color"));
            assertEquals(EmbedSettings.DEFAULT_THUMBNAIL_URL, request.cardThumbnail());
            assertEquals(MessageSettings.DEFAULT_AVATAR_URL, payload.getString("avatar_url"));
            assertTrue(request.cardTexts().contains("### [app #1](" + r.getURL() + "job/app/1/)"), request.cardText());
            assertEquals("app #1", request.cardTitle());
            String text = request.cardText();
            assertTrue(text.contains("**Result:** Success\n**Duration:** "), text);
            assertTrue(text.contains("**Artifacts**\n[app-1.0.jar](" + r.getURL()
                    + "job/app/1/artifact/build/libs/app-1.0.jar)"), text);
            assertFalse(text.contains("sources"), text);
            assertTrue(request.cardTexts().get(request.cardTexts().size() - 1).matches("-# <t:\\d+:f>"), text);

            assertEquals(List.of("app-1.0.jar"), request.cardFiles());
            List<FakeDiscord.Part> files = request.files();
            assertEquals(1, files.size());
            assertEquals("app-1.0.jar", files.get(0).filename());
            assertArrayEquals(jar, files.get(0).content());
            JSONArray attachments = payload.getJSONArray("attachments");
            assertEquals(1, attachments.size());
            assertEquals("app-1.0.jar", attachments.getJSONObject(0).getString("filename"));

            DevBuildAction action = build.getAction(DevBuildAction.class);
            assertNotNull(action);
            assertEquals(1, action.getMessageIds().size());
        }
    }

    @Test
    void afterEmbedPlacementPostsTheEmbedThenTheFiles(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            ArtifactSettings artifactSettings = new ArtifactSettings();
            artifactSettings.setPlacement(FilePlacement.AFTER_EMBED);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            publisher.setArtifacts(artifactSettings);
            byte[] jar = "jar-content".getBytes(StandardCharsets.UTF_8);
            FreeStyleProject p = project(r, Map.of(
                    "build/libs/app-1.0.jar", jar,
                    "build/libs/app-1.0-sources.jar", "sources".getBytes(StandardCharsets.UTF_8)), publisher);

            FreeStyleBuild build = r.buildAndAssertSuccess(p);

            // AFTER_EMBED: the classic embed first, then the files in a second message.
            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            FakeDiscord.Request request = requests.get(0);
            assertFalse(request.isMultipart());
            assertTrue(request.files().isEmpty());
            assertFalse(request.payload().has("attachments"));
            JSONObject embed = request.embed();
            assertEquals(EmbedSettings.DEFAULT_THUMBNAIL_URL, embed.getJSONObject("thumbnail").getString("url"));
            assertEquals(MessageSettings.DEFAULT_AVATAR_URL, request.payload().getString("avatar_url"));
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

            FakeDiscord.Request filesMessage = requests.get(1);
            assertTrue(filesMessage.isMultipart());
            assertFalse(filesMessage.payload().has("embeds"));
            assertFalse(filesMessage.payload().has("content"));
            assertFalse(filesMessage.payload().has("components"));
            assertFalse(request.query().containsKey("with_components"));
            assertTrue(filesMessage.receivedAtMillis() >= request.receivedAtMillis());
            List<FakeDiscord.Part> files = filesMessage.files();
            assertEquals(1, files.size());
            assertEquals("files[0]", files.get(0).name());
            assertEquals("app-1.0.jar", files.get(0).filename());
            assertArrayEquals(jar, files.get(0).content());
            JSONArray attachments = filesMessage.payload().getJSONArray("attachments");
            assertEquals(1, attachments.size());
            assertEquals(0, attachments.getJSONObject(0).getInt("id"));
            assertEquals("app-1.0.jar", attachments.getJSONObject(0).getString("filename"));

            DevBuildAction action = build.getAction(DevBuildAction.class);
            assertNotNull(action);
            assertEquals(2, action.getMessageIds().size());
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
            assertEquals(0xE74C3C, request.card().getInt("accent_color"));
            assertTrue(request.cardText().contains("**Result:** Failure"), request.cardText());
            assertTrue(request.cardText().contains("(not attached)"), request.cardText());
            assertTrue(request.cardFiles().isEmpty());
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

            // The first batch goes inside the card; further batches follow as classic messages.
            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(3, requests.size());
            assertTrue(requests.get(0).isComponentsV2());
            assertEquals(List.of("a.jar"), requests.get(0).cardFiles());
            assertEquals(List.of("a.jar"), requests.get(0).files().stream().map(FakeDiscord.Part::filename).toList());
            assertEquals(List.of("b.jar"), requests.get(1).files().stream().map(FakeDiscord.Part::filename).toList());
            assertEquals(List.of("c.jar"), requests.get(2).files().stream().map(FakeDiscord.Part::filename).toList());
            for (int i = 1; i < requests.size(); i++) {
                JSONObject followUp = requests.get(i).payload();
                assertFalse(followUp.has("embeds"));
                assertFalse(followUp.has("components"));
                assertFalse(followUp.has("flags"));
                assertFalse(requests.get(i).query().containsKey("with_components"));
                assertEquals(1, followUp.getJSONArray("attachments").size());
            }
            String artifactsField = requests.get(0).cardText();
            assertTrue(artifactsField.contains("[huge.jar](" + r.getURL() + "job/app/1/artifact/build/libs/huge.jar)"
                    + " · 1.5 MiB (not attached)"), artifactsField);
            for (FakeDiscord.Request request : requests) {
                long total = request.files().stream().mapToLong(f -> f.content().length).sum();
                assertTrue(total <= 1024 * 1024, "batch over budget: " + total);
            }
        }
    }

    @Test
    void withEmbedPlacementAttachesFilesToTheEmbedMessage(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            ArtifactSettings artifacts = new ArtifactSettings();
            artifacts.setPlacement(FilePlacement.WITH_EMBED);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            publisher.setArtifacts(artifacts);
            FreeStyleProject p = project(r, Map.of("build/libs/app.jar", bytes(10)), publisher);

            r.buildAndAssertSuccess(p);

            FakeDiscord.Request request = discord.only();
            assertTrue(request.isMultipart());
            assertEquals("app #1", request.embed().getString("title"));
            assertEquals(List.of("app.jar"), request.files().stream().map(FakeDiscord.Part::filename).toList());
            assertEquals(1, request.payload().getJSONArray("attachments").size());
        }
    }

    @Test
    void withoutEmbedFilesGoInASingleMessage(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            EmbedSettings embed = new EmbedSettings();
            embed.setEnabled(false);
            DevBuildPublisher publisher = new DevBuildPublisher();
            publisher.setWebhookName(TestSupport.WEBHOOK_NAME);
            publisher.setEmbed(embed);
            FreeStyleProject p = project(r, Map.of("build/libs/app.jar", bytes(10)), publisher);

            r.buildAndAssertSuccess(p);

            FakeDiscord.Request request = discord.only();
            assertTrue(request.isMultipart());
            assertFalse(request.payload().has("embeds"));
            assertEquals(List.of("app.jar"), request.files().stream().map(FakeDiscord.Part::filename).toList());
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
            assertFalse(request.cardText().contains("Artifacts"), request.cardText());
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
