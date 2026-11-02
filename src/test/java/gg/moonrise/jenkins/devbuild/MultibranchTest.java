package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.moonrise.jenkins.devbuild.config.DevBuildFolderProperty;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jenkins.branch.BranchSource;
import jenkins.scm.impl.mock.MockSCMController;
import jenkins.scm.impl.mock.MockSCMDiscoverBranches;
import jenkins.scm.impl.mock.MockSCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * A real Multibranch Pipeline: the folder property on the project configures every branch job, with no DevBuild
 * code in the Jenkinsfile.
 */
@WithJenkins
class MultibranchTest {

    private static final String JENKINSFILE = """
            node {
              writeFile file: 'build/libs/mod.jar', text: "jar for ${env.BRANCH_NAME}"
              archiveArtifacts 'build/libs/*.jar'
            }
            """;

    @Test
    void branchJobsInheritTheMultibranchFolderProperty(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord(); MockSCMController scm = MockSCMController.create()) {
            TestSupport.registerWebhook(discord);
            scm.createRepository("repo");
            scm.createBranch("repo", "feature");
            scm.addFile("repo", "master", "add Jenkinsfile", "Jenkinsfile", JENKINSFILE.getBytes(StandardCharsets.UTF_8));
            scm.addFile("repo", "feature", "add Jenkinsfile", "Jenkinsfile", JENKINSFILE.getBytes(StandardCharsets.UTF_8));

            WorkflowMultiBranchProject project = r.jenkins.createProject(WorkflowMultiBranchProject.class, "game");
            project.setDisplayName("Game Server");
            DevBuildFolderProperty property = new DevBuildFolderProperty();
            property.setWebhookName(TestSupport.WEBHOOK_NAME);
            NotifyConditions conditions = new NotifyConditions();
            conditions.setBranchPattern("master");
            property.setConditions(conditions);
            project.getProperties().add(property);
            project.getSourcesList().add(new BranchSource(new MockSCMSource(scm, "repo", new MockSCMDiscoverBranches())));

            project.scheduleBuild2(0).getFuture().get();
            r.waitUntilNoActivity();

            WorkflowJob master = project.getItem("master");
            WorkflowJob feature = project.getItem("feature");
            assertNotNull(master);
            assertNotNull(feature);
            r.assertBuildStatusSuccess(master.getBuildByNumber(1));
            r.assertBuildStatusSuccess(feature.getBuildByNumber(1));
            r.assertLogContains("branch 'feature' does not match branchPattern 'master'", feature.getBuildByNumber(1));

            FakeDiscord.Request request = discord.only();
            assertEquals("Game Server #1", request.cardTitle());
            assertTrue(request.cardText().contains("**Branch:** `master`"), request.cardText());
            assertEquals(List.of("mod.jar"), request.cardFiles());
            List<FakeDiscord.Part> files = request.files();
            assertEquals("mod.jar", files.get(0).filename());
            assertEquals("jar for master", files.get(0).text());
        }
    }
}
