package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import gg.moonrise.jenkins.devbuild.build.DevBuildAction;
import gg.moonrise.jenkins.devbuild.build.DevBuildPublisher;
import gg.moonrise.jenkins.devbuild.config.DevBuildFolderProperty;
import gg.moonrise.jenkins.devbuild.config.DevBuildGlobalConfiguration;
import gg.moonrise.jenkins.devbuild.config.DevBuildJobProperty;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSource;
import gg.moonrise.jenkins.devbuild.settings.EmbedField;
import gg.moonrise.jenkins.devbuild.settings.FilePlacement;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import gg.moonrise.jenkins.devbuild.settings.OversizePolicy;
import gg.moonrise.jenkins.devbuild.webhook.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.webhook.NamedWebhook;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Configuration and build data saved by releases up to 1.2.1, when the classes lived in the root package, still loads.
 */
@WithJenkins
class LegacyDataTest {

    private static final String GLOBAL_CONFIG_FILE = "gg.moonrise.jenkins.devbuild.DevBuildGlobalConfiguration.xml";

    private static final String LEGACY_GLOBAL_CONFIG = """
            <?xml version='1.1' encoding='UTF-8'?>
            <gg.moonrise.jenkins.devbuild.DevBuildGlobalConfiguration plugin="devbuild@1.2.1">
              <webhooks>
                <gg.moonrise.jenkins.devbuild.NamedWebhook>
                  <name>devbuilds</name>
                  <webhook>
                    <credentialsId>discord-devbuilds</credentialsId>
                    <threadId>123456789012345678</threadId>
                  </webhook>
                </gg.moonrise.jenkins.devbuild.NamedWebhook>
              </webhooks>
              <defaultWebhookName>devbuilds</defaultWebhookName>
              <message>
                <username>Legacy bot</username>
              </message>
              <conditions>
                <notifyOnSuccess>true</notifyOnSuccess>
                <notifyOnUnstable>true</notifyOnUnstable>
                <notifyOnFailure>true</notifyOnFailure>
                <notifyOnAborted>true</notifyOnAborted>
                <notifyOnNotBuilt>false</notifyOnNotBuilt>
                <notifyOnBackToNormal>true</notifyOnBackToNormal>
                <branchPattern>main</branchPattern>
              </conditions>
              <timeoutSeconds>45</timeoutSeconds>
              <maxRetries>1</maxRetries>
            </gg.moonrise.jenkins.devbuild.DevBuildGlobalConfiguration>
            """;

    private static final String LEGACY_JOB_CONFIG = """
            <?xml version='1.1' encoding='UTF-8'?>
            <project>
              <description></description>
              <keepDependencies>false</keepDependencies>
              <properties>
                <gg.moonrise.jenkins.devbuild.DevBuildJobProperty plugin="devbuild@1.2.1">
                  <webhookName>devbuilds</webhookName>
                  <autoPublish>false</autoPublish>
                  <skipIfStepUsed>true</skipIfStepUsed>
                </gg.moonrise.jenkins.devbuild.DevBuildJobProperty>
              </properties>
              <scm class="hudson.scm.NullSCM"/>
              <canRoam>true</canRoam>
              <disabled>false</disabled>
              <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
              <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
              <triggers/>
              <concurrentBuild>false</concurrentBuild>
              <builders/>
              <publishers>
                <gg.moonrise.jenkins.devbuild.DevBuildPublisher plugin="devbuild@1.2.1">
                  <webhook>
                    <credentialsId>job-webhook</credentialsId>
                    <threadName>Release notes</threadName>
                  </webhook>
                  <message>
                    <username>Job bot</username>
                  </message>
                  <embed>
                    <title>Old title</title>
                    <fields>
                      <gg.moonrise.jenkins.devbuild.EmbedField>
                        <name>Environment</name>
                        <value>staging</value>
                        <inline>true</inline>
                      </gg.moonrise.jenkins.devbuild.EmbedField>
                    </fields>
                  </embed>
                  <artifacts>
                    <enabled>true</enabled>
                    <includes>**/*.jar</includes>
                    <excludes></excludes>
                    <source>WORKSPACE</source>
                    <maxFiles>4</maxFiles>
                    <maxUploadSizeMb>8</maxUploadSizeMb>
                    <onlyOnSuccess>true</onlyOnSuccess>
                    <attachOnUnstable>false</attachOnUnstable>
                    <oversizePolicy>SKIP</oversizePolicy>
                    <placement>AFTER_EMBED</placement>
                  </artifacts>
                  <result>FAILURE</result>
                </gg.moonrise.jenkins.devbuild.DevBuildPublisher>
              </publishers>
              <buildWrappers/>
            </project>
            """;

    /**
     * Turns XML written by this version back into what releases before the package move wrote.
     */
    private static String toLegacyClassNames(String xml) {
        return xml.replaceAll("gg\\.moonrise\\.jenkins\\.devbuild\\.(build|config|settings|webhook)\\.",
                "gg.moonrise.jenkins.devbuild.");
    }

    private static void rewriteWithLegacyClassNames(File file) throws IOException {
        Path path = file.toPath();
        String xml = Files.readString(path, StandardCharsets.UTF_8);
        String legacy = toLegacyClassNames(xml);
        assertFalse(xml.equals(legacy), "expected moved class names in " + file);
        Files.writeString(path, legacy, StandardCharsets.UTF_8);
    }

    @Test
    void legacyGlobalConfigurationLoads(JenkinsRule r) throws Exception {
        Path file = r.jenkins.getRootDir().toPath().resolve(GLOBAL_CONFIG_FILE);
        Files.writeString(file, LEGACY_GLOBAL_CONFIG, StandardCharsets.UTF_8);

        // A new instance runs load() from its constructor, which is how Jenkins reads the file at startup.
        DevBuildGlobalConfiguration global = new DevBuildGlobalConfiguration();

        assertEquals("devbuilds", global.getDefaultWebhookName());
        NamedWebhook named = global.findWebhook("devbuilds").orElseThrow();
        DiscordWebhook webhook = named.getWebhook();
        assertEquals("discord-devbuilds", webhook.getCredentialsId());
        assertEquals("123456789012345678", webhook.getThreadId());
        assertEquals("Legacy bot", global.getMessage().getUsername());
        NotifyConditions conditions = global.getConditions();
        assertTrue(conditions.isNotifyOnAborted());
        assertEquals("main", conditions.getBranchPattern());
        assertEquals(45, global.getTimeoutSeconds());
        assertEquals(1, global.getMaxRetries());
    }

    @Test
    void globalConfigurationKeepsItsFileName(JenkinsRule r) throws Exception {
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        global.setTimeoutSeconds(12);

        Path file = r.jenkins.getRootDir().toPath().resolve(GLOBAL_CONFIG_FILE);
        assertTrue(Files.exists(file), "settings are not saved to " + GLOBAL_CONFIG_FILE);
        assertFalse(Files.exists(r.jenkins.getRootDir().toPath().resolve(DevBuildGlobalConfiguration.class.getName() + ".xml")));
        assertEquals(12, new DevBuildGlobalConfiguration().getTimeoutSeconds());
    }

    @Test
    void legacyFreestyleJobLoads(JenkinsRule r) throws Exception {
        Path jobDir = r.jenkins.getRootDir().toPath().resolve("jobs").resolve("legacy");
        Files.createDirectories(jobDir);
        Files.writeString(jobDir.resolve("config.xml"), LEGACY_JOB_CONFIG, StandardCharsets.UTF_8);

        r.jenkins.reload();

        FreeStyleProject job = r.jenkins.getItemByFullName("legacy", FreeStyleProject.class);
        assertNotNull(job, "job did not load");
        DevBuildJobProperty property = job.getProperty(DevBuildJobProperty.class);
        assertNotNull(property, "job property was dropped");
        assertEquals("devbuilds", property.getWebhookName());
        assertFalse(property.isAutoPublish());

        DevBuildPublisher publisher = job.getPublishersList().get(DevBuildPublisher.class);
        assertNotNull(publisher, "post-build action was dropped");
        assertEquals("job-webhook", publisher.getWebhook().getCredentialsId());
        assertEquals("Release notes", publisher.getWebhook().getThreadName());
        assertEquals("Job bot", publisher.getMessage().getUsername());
        assertEquals("Old title", publisher.getEmbed().getTitle());
        List<EmbedField> fields = publisher.getEmbed().getFields();
        assertEquals(1, fields.size());
        assertEquals("Environment", fields.get(0).getName());
        assertEquals("staging", fields.get(0).getValue());
        assertTrue(fields.get(0).isInline());
        ArtifactSettings artifacts = publisher.getArtifacts();
        assertEquals(ArtifactSource.WORKSPACE, artifacts.getSource());
        assertEquals(4, artifacts.getMaxFiles());
        assertEquals(OversizePolicy.SKIP, artifacts.getOversizePolicy());
        assertEquals(FilePlacement.AFTER_EMBED, artifacts.getPlacement());
        assertEquals("FAILURE", publisher.getResult());
    }

    @Test
    void legacyFolderPropertyAndBuildActionLoad(JenkinsRule r) throws Exception {
        Folder folder = r.jenkins.createProject(Folder.class, "team");
        DevBuildFolderProperty property = new DevBuildFolderProperty();
        property.setWebhookName("team-hook");
        property.setSkipIfStepUsed(false);
        folder.getProperties().add(property);
        folder.save();
        FreeStyleProject job = folder.createProject(FreeStyleProject.class, "app");
        FreeStyleBuild build = r.buildAndAssertSuccess(job);
        DevBuildAction.record(build, List.of("111", "222"));
        build.save();

        rewriteWithLegacyClassNames(new File(folder.getRootDir(), "config.xml"));
        rewriteWithLegacyClassNames(new File(build.getRootDir(), "build.xml"));
        r.jenkins.reload();

        Folder reloadedFolder = r.jenkins.getItemByFullName("team", Folder.class);
        DevBuildFolderProperty reloadedProperty = reloadedFolder.getProperties().get(DevBuildFolderProperty.class);
        assertNotNull(reloadedProperty, "folder property was dropped");
        assertEquals("team-hook", reloadedProperty.getWebhookName());
        assertFalse(reloadedProperty.isSkipIfStepUsed());

        FreeStyleProject reloadedJob = r.jenkins.getItemByFullName("team/app", FreeStyleProject.class);
        DevBuildAction action = reloadedJob.getBuildByNumber(1).getAction(DevBuildAction.class);
        assertNotNull(action, "build action was dropped");
        assertEquals(1, action.getInvocations());
        assertEquals(List.of("111", "222"), action.getMessageIds());
    }
}
