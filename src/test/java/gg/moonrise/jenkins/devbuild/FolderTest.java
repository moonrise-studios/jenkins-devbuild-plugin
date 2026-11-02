package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.hudson.plugins.folder.properties.FolderCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.FreeStyleProject;
import hudson.util.Secret;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Folder properties, automatic publishing and inheritance. Uses real cloudbees-folder folders: the test harness'
 * {@code MockFolder} cannot hold {@code AbstractFolderProperty} instances.
 */
@WithJenkins
class FolderTest {

    private static final String ARCHIVE_SCRIPT = """
            node {
              writeFile file: 'build/libs/team.jar', text: 'team jar'
              archiveArtifacts 'build/libs/*.jar'
            }
            """;

    private static DevBuildFolderProperty autoPublishing(String webhookName) {
        DevBuildFolderProperty property = new DevBuildFolderProperty();
        property.setWebhookName(webhookName);
        return property;
    }

    private static WorkflowJob job(Folder folder, String name, String script) throws Exception {
        WorkflowJob job = folder.createProject(WorkflowJob.class, name);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job;
    }

    @Test
    void folderPropertyPublishesAutomatically(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            Folder folder = r.jenkins.createProject(Folder.class, "team");
            folder.getProperties().add(autoPublishing(TestSupport.WEBHOOK_NAME));
            WorkflowJob job = job(folder, "app", ARCHIVE_SCRIPT);

            WorkflowRun run = r.buildAndAssertSuccess(job);

            List<FakeDiscord.Request> requests = discord.requests();
            assertEquals(2, requests.size());
            assertEquals("app #1", requests.get(0).embed().getString("title"));
            assertEquals("team.jar", requests.get(1).files().get(0).filename());
            assertEquals("team jar", requests.get(1).files().get(0).text());
            assertNull(run.getAction(DevBuildAction.class));
            r.assertLogContains("[DevBuild] Posting SUCCESS", run);
        }
    }

    @Test
    void stepAndAutoPublishDoNotDoublePost(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            Folder folder = r.jenkins.createProject(Folder.class, "team");
            folder.getProperties().add(autoPublishing(TestSupport.WEBHOOK_NAME));
            WorkflowJob job = job(folder, "app", ARCHIVE_SCRIPT + "discordDevBuild message: [content: 'from step']\n");

            WorkflowRun run = r.buildAndAssertSuccess(job);

            assertEquals(2, discord.requests().size(), "embed message and files message from the step only");
            assertEquals("from step", discord.requests().get(0).payload().getString("content"));
            r.assertLogContains("Discord step already ran for this build; not posting again", run);

            DevBuildFolderProperty property = folder.getProperties().get(DevBuildFolderProperty.class);
            property.setSkipIfStepUsed(false);
            r.buildAndAssertSuccess(job);
            assertEquals(6, discord.requests().size(), "skipIfStepUsed=false posts from both the step and the listener");
        }
    }

    @Test
    void autoPublishOffDoesNothing(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            Folder folder = r.jenkins.createProject(Folder.class, "team");
            DevBuildFolderProperty property = autoPublishing(TestSupport.WEBHOOK_NAME);
            property.setAutoPublish(false);
            folder.getProperties().add(property);
            WorkflowJob job = job(folder, "app", ARCHIVE_SCRIPT);

            r.buildAndAssertSuccess(job);

            assertTrue(discord.requests().isEmpty());
        }
    }

    @Test
    void nearestLevelWinsPerSection(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord(); FakeDiscord other = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            TestSupport.registerWebhook(other, "other");
            MessageSettings globalMessage = new MessageSettings();
            globalMessage.setContent("global content");
            globalMessage.setUsername("Global bot");
            DevBuildGlobalConfiguration.get().setMessage(globalMessage);
            DevBuildGlobalConfiguration.get().setDefaultWebhookName("other");

            Folder outer = r.jenkins.createProject(Folder.class, "outer");
            DevBuildFolderProperty outerProperty = new DevBuildFolderProperty();
            outerProperty.setWebhookName(TestSupport.WEBHOOK_NAME);
            outerProperty.setAutoPublish(false);
            EmbedSettings outerEmbed = new EmbedSettings();
            outerEmbed.setTitle("outer ${BUILD_NUMBER}");
            outerProperty.setEmbed(outerEmbed);
            outer.getProperties().add(outerProperty);

            Folder inner = outer.createProject(Folder.class, "inner");
            DevBuildFolderProperty innerProperty = new DevBuildFolderProperty();
            EmbedSettings innerEmbed = new EmbedSettings();
            innerEmbed.setTitle("inner ${BUILD_NUMBER}");
            innerProperty.setEmbed(innerEmbed);
            DiscordWebhook threadOnly = new DiscordWebhook();
            threadOnly.setThreadId("1234567890123456789");
            innerProperty.setWebhook(threadOnly);
            inner.getProperties().add(innerProperty);

            WorkflowJob job = job(inner, "app", "discordDevBuild()");
            WorkflowRun run = r.buildAndAssertSuccess(job);

            assertTrue(other.requests().isEmpty(), "the outer folder's webhook must win over the global default");
            // The inner folder is the nearest level; it auto-publishes but skips because the step already ran.
            r.assertLogContains("not posting again", run);
            FakeDiscord.Request request = discord.only();
            assertEquals("inner 1", request.embed().getString("title"));
            assertEquals("global content", request.payload().getString("content"));
            assertEquals("Global bot", request.payload().getString("username"));
            assertEquals("1234567890123456789", request.query().get("thread_id"), "thread override from inner folder");
        }
    }

    @Test
    void jobPropertyOverridesFolderAndWorksForFreestyle(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            Folder folder = r.jenkins.createProject(Folder.class, "team");
            folder.getProperties().add(autoPublishing(TestSupport.WEBHOOK_NAME));
            FreeStyleProject p = folder.createProject(FreeStyleProject.class, "classic");
            DevBuildJobProperty property = new DevBuildJobProperty();
            MessageSettings message = new MessageSettings();
            message.setContent("job level");
            property.setMessage(message);
            p.addProperty(property);

            r.buildAndAssertSuccess(p);

            assertEquals("job level", discord.only().payload().getString("content"));

            property.setAutoPublish(false);
            r.buildAndAssertSuccess(p);
            assertEquals(1, discord.requests().size(), "the job property is the nearest level and disables auto publish");
        }
    }

    @Test
    void jobPropertyFromPipelineProperties(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            WorkflowJob job = TestSupport.pipeline(r, "props", """
                    properties([discordDevBuildJob(webhookName: 'devbuilds', embed: [title: 'props ${BUILD_NUMBER}'])])
                    """);

            r.buildAndAssertSuccess(job);

            assertEquals("props 1", discord.only().embed().getString("title"));
            assertEquals(TestSupport.WEBHOOK_NAME, job.getProperty(DevBuildJobProperty.class).getWebhookName());
        }
    }

    @Test
    void folderScopedCredentials(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            Folder folder = r.jenkins.createProject(Folder.class, "secure");
            CredentialsStore store = null;
            for (CredentialsStore candidate : CredentialsProvider.lookupStores(folder)) {
                if (candidate.getProvider() instanceof FolderCredentialsProvider && candidate.getContext() == folder) {
                    store = candidate;
                }
            }
            store.addCredentials(Domain.global(), new StringCredentialsImpl(CredentialsScope.GLOBAL, "folder-hook",
                    "folder webhook", Secret.fromString(discord.url())));
            DevBuildFolderProperty property = new DevBuildFolderProperty();
            DiscordWebhook webhook = new DiscordWebhook();
            webhook.setCredentialsId("folder-hook");
            property.setWebhook(webhook);
            folder.getProperties().add(property);
            WorkflowJob job = job(folder, "app", "echo 'built'");

            r.buildAndAssertSuccess(job);

            assertEquals(1, discord.requests().size());
            assertTrue(job.getLastBuild().getActions(DevBuildAction.class).isEmpty());
        }
    }
}
