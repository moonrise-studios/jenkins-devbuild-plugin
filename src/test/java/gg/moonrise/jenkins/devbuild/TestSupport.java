package gg.moonrise.jenkins.devbuild;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.util.Secret;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Shared setup for the Jenkins integration tests.
 */
final class TestSupport {

    static final String WEBHOOK_NAME = "devbuilds";

    private TestSupport() {
    }

    /**
     * Stores the fake webhook URL as a Secret text credential and registers it as a named global webhook.
     */
    static DiscordWebhook registerWebhook(FakeDiscord discord, String name) throws IOException {
        String credentialsId = "discord-" + name;
        SystemCredentialsProvider provider = SystemCredentialsProvider.getInstance();
        provider.getCredentials().add(new StringCredentialsImpl(CredentialsScope.GLOBAL, credentialsId,
                "Discord webhook " + name, Secret.fromString(discord.url())));
        provider.save();
        DiscordWebhook webhook = new DiscordWebhook();
        webhook.setCredentialsId(credentialsId);
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        List<NamedWebhook> webhooks = new ArrayList<>(global.getWebhooks());
        webhooks.add(new NamedWebhook(name, webhook));
        global.setWebhooks(webhooks);
        return webhook;
    }

    static DiscordWebhook registerWebhook(FakeDiscord discord) throws IOException {
        return registerWebhook(discord, WEBHOOK_NAME);
    }

    static WorkflowJob pipeline(JenkinsRule r, String name, String script) throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class, name);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job;
    }
}
