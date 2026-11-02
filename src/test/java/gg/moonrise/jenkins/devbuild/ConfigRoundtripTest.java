package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Submits every configuration form unchanged and checks the data survives, so each Jelly view binds correctly.
 */
@WithJenkins
class ConfigRoundtripTest {

    private static final String SECRET_URL = "https://discord.com/api/webhooks/123456789012345678/very-secret-token";

    private static DiscordWebhook inlineWebhook() {
        DiscordWebhook webhook = new DiscordWebhook();
        webhook.setWebhookUrl(Secret.fromString(SECRET_URL));
        webhook.setThreadName("Build ${BUILD_NUMBER}");
        webhook.setAppliedTags("111111111111111111");
        return webhook;
    }

    private static MessageSettings message() {
        MessageSettings message = new MessageSettings();
        message.setContent("<@&42> ${PROJECT_NAME} is out");
        message.setUsername("Builds");
        message.setAvatarUrl("https://example.com/avatar.png");
        message.setTts(true);
        message.setSuppressNotifications(true);
        message.setMentionRoles(false);
        message.setMentionEveryone(true);
        return message;
    }

    private static EmbedSettings embed() {
        EmbedSettings embed = new EmbedSettings();
        embed.setTitle("Custom ${BUILD_NUMBER}");
        embed.setDescription("Line 1\nLine 2");
        embed.setAuthorName("Moonrise");
        embed.setFooterText("footer");
        embed.setThumbnailUrl("https://example.com/t.png");
        embed.setTimestamp(false);
        embed.setSuccessColor("#00FF00");
        embed.setShowChanges(false);
        embed.setMaxChanges(3);
        embed.setFields(List.of(new EmbedField("Version", "${VERSION}", true), new EmbedField("Notes", "n", false)));
        return embed;
    }

    private static ArtifactSettings artifacts() {
        ArtifactSettings artifacts = new ArtifactSettings();
        artifacts.setIncludes("build/libs/*.jar");
        artifacts.setExcludes("**/*-dev.jar");
        artifacts.setSource(ArtifactSource.ARCHIVED);
        artifacts.setMaxFiles(20);
        artifacts.setMaxUploadSizeMb(25);
        artifacts.setOnlyOnSuccess(false);
        artifacts.setAttachOnUnstable(true);
        artifacts.setOversizePolicy(OversizePolicy.SKIP);
        artifacts.setPlacement(FilePlacement.WITH_EMBED);
        return artifacts;
    }

    private static NotifyConditions conditions() {
        NotifyConditions conditions = new NotifyConditions();
        conditions.setNotifyOnSuccess(false);
        conditions.setNotifyOnAborted(true);
        conditions.setNotifyOnBackToNormal(false);
        conditions.setBranchPattern("main|release/.*");
        return conditions;
    }

    @Test
    void publisherRoundtrip(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        DevBuildPublisher publisher = new DevBuildPublisher();
        publisher.setWebhook(inlineWebhook());
        publisher.setMessage(message());
        publisher.setEmbed(embed());
        publisher.setArtifacts(artifacts());
        publisher.setConditions(conditions());
        publisher.setResult("UNSTABLE");
        p.getPublishersList().add(publisher);

        r.configRoundtrip(p);

        DevBuildPublisher after = p.getPublishersList().get(DevBuildPublisher.class);
        r.assertEqualDataBoundBeans(publisher, after);
        assertEquals(SECRET_URL, after.getWebhook().getWebhookUrl().getPlainText());
        String xml = Files.readString(p.getConfigFile().getFile().toPath(), StandardCharsets.UTF_8);
        assertFalse(xml.contains("very-secret-token"), "webhook URL stored in plain text");
    }

    @Test
    void emptyPublisherRoundtrip(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        DevBuildPublisher publisher = new DevBuildPublisher();
        publisher.setWebhookName("devbuilds");
        p.getPublishersList().add(publisher);
        DevBuildGlobalConfiguration.get().setWebhooks(List.of(new NamedWebhook("devbuilds", new DiscordWebhook())));

        r.configRoundtrip(p);

        DevBuildPublisher after = p.getPublishersList().get(DevBuildPublisher.class);
        assertEquals("devbuilds", after.getWebhookName());
        assertNull(after.getWebhook());
        assertNull(after.getMessage());
        assertNull(after.getEmbed());
        assertNull(after.getArtifacts());
        assertNull(after.getConditions());
    }

    @Test
    void jobPropertyRoundtrip(JenkinsRule r) throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class, "p");
        DevBuildJobProperty property = new DevBuildJobProperty();
        property.setAutoPublish(false);
        property.setSkipIfStepUsed(false);
        property.setWebhook(inlineWebhook());
        property.setEmbed(embed());
        property.setArtifacts(artifacts());
        job.addProperty(property);

        r.configRoundtrip(job);

        DevBuildJobProperty after = job.getProperty(DevBuildJobProperty.class);
        assertNotNull(after);
        r.assertEqualDataBoundBeans(property, after);
        String xml = Files.readString(job.getConfigFile().getFile().toPath(), StandardCharsets.UTF_8);
        assertFalse(xml.contains("very-secret-token"));
    }

    @Test
    void jobPropertyIsOptional(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        r.configRoundtrip(p);
        assertNull(p.getProperty(DevBuildJobProperty.class));
    }

    @Test
    void folderPropertyRoundtrip(JenkinsRule r) throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "team");
        DevBuildFolderProperty property = new DevBuildFolderProperty();
        DiscordWebhook webhook = new DiscordWebhook();
        webhook.setCredentialsId("discord-team");
        webhook.setThreadId("1234567890123456789");
        property.setWebhook(webhook);
        property.setMessage(message());
        property.setConditions(conditions());
        folder.getProperties().add(property);

        r.configRoundtrip(folder);

        DevBuildFolderProperty after = folder.getProperties().get(DevBuildFolderProperty.class);
        assertNotNull(after);
        r.assertEqualDataBoundBeans(property, after);
    }

    @Test
    void folderPropertyIsOptional(JenkinsRule r) throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "plain");
        r.configRoundtrip(folder);
        assertNull(folder.getProperties().get(DevBuildFolderProperty.class));
    }

    @Test
    void globalConfigurationRoundtrip(JenkinsRule r) throws Exception {
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        DiscordWebhook second = new DiscordWebhook();
        second.setCredentialsId("discord-releases");
        second.setThreadId("1234567890123456789");
        global.setWebhooks(List.of(new NamedWebhook("devbuilds", inlineWebhook()), new NamedWebhook("releases", second)));
        global.setDefaultWebhookName("releases");
        global.setMessage(message());
        global.setEmbed(embed());
        global.setArtifacts(artifacts());
        global.setConditions(conditions());
        global.setTimeoutSeconds(12);
        global.setMaxRetries(5);

        r.configRoundtrip();

        DevBuildGlobalConfiguration after = DevBuildGlobalConfiguration.get();
        assertEquals(2, after.getWebhooks().size());
        r.assertEqualDataBoundBeans(global.getWebhooks(), after.getWebhooks());
        assertEquals("releases", after.getDefaultWebhookName());
        r.assertEqualDataBoundBeans(message(), after.getMessage());
        r.assertEqualDataBoundBeans(embed(), after.getEmbed());
        r.assertEqualDataBoundBeans(artifacts(), after.getArtifacts());
        r.assertEqualDataBoundBeans(conditions(), after.getConditions());
        assertEquals(12, after.getTimeoutSeconds());
        assertEquals(5, after.getMaxRetries());
        String xml = Files.readString(r.jenkins.getRootDir().toPath().resolve(DevBuildGlobalConfiguration.class.getName() + ".xml"),
                StandardCharsets.UTF_8);
        assertFalse(xml.contains("very-secret-token"));
        assertTrue(xml.contains("<webhookUrl>{"), "secret should be stored encrypted");
    }

    @Test
    void defaultSectionsRoundtripWithTheNewDefaults(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        DevBuildPublisher publisher = new DevBuildPublisher();
        publisher.setEmbed(new EmbedSettings());
        publisher.setArtifacts(new ArtifactSettings());
        p.getPublishersList().add(publisher);

        r.configRoundtrip(p);

        DevBuildPublisher after = p.getPublishersList().get(DevBuildPublisher.class);
        assertEquals(EmbedSettings.DEFAULT_THUMBNAIL_URL, after.getEmbed().getThumbnailUrl());
        assertEquals(FilePlacement.AFTER_EMBED, after.getArtifacts().getPlacement());
    }

    @Test
    void blankThumbnailRoundtripsAsNoThumbnail(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        DevBuildPublisher publisher = new DevBuildPublisher();
        EmbedSettings embed = new EmbedSettings();
        embed.setThumbnailUrl("");
        publisher.setEmbed(embed);
        p.getPublishersList().add(publisher);

        r.configRoundtrip(p);

        assertEquals("", p.getPublishersList().get(DevBuildPublisher.class).getEmbed().getThumbnailUrl());
        EmbedSettings reloaded = (EmbedSettings) Jenkins.XSTREAM2.fromXML(Jenkins.XSTREAM2.toXML(embed));
        assertEquals("", reloaded.getThumbnailUrl());
    }

    @Test
    void unconfiguredEmbedFormShowsTheDefaultThumbnail(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getPublishersList().add(new DevBuildPublisher());
        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            String page = wc.getPage(p, "configure").getWebResponse().getContentAsString();
            assertTrue(page.contains(EmbedSettings.DEFAULT_THUMBNAIL_URL), "thumbnail field is not pre-filled");
        }
    }

    @Test
    void olderXmlGetsTheNewDefaults(JenkinsRule r) {
        EmbedSettings embed = (EmbedSettings) Jenkins.XSTREAM2.fromXML(
                "<gg.moonrise.jenkins.devbuild.EmbedSettings><title>Old</title></gg.moonrise.jenkins.devbuild.EmbedSettings>");
        assertEquals("Old", embed.getTitle());
        assertEquals(EmbedSettings.DEFAULT_THUMBNAIL_URL, embed.getThumbnailUrl());
        ArtifactSettings artifacts = (ArtifactSettings) Jenkins.XSTREAM2.fromXML(
                "<gg.moonrise.jenkins.devbuild.ArtifactSettings><maxFiles>3</maxFiles></gg.moonrise.jenkins.devbuild.ArtifactSettings>");
        assertEquals(3, artifacts.getMaxFiles());
        assertEquals(FilePlacement.AFTER_EMBED, artifacts.getPlacement());
    }

    @Test
    void globalSectionsCanBeCleared(JenkinsRule r) throws Exception {
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        global.setMessage(message());
        r.configRoundtrip();
        assertNotNull(DevBuildGlobalConfiguration.get().getMessage());
        assertNull(DevBuildGlobalConfiguration.get().getEmbed());
    }
}
