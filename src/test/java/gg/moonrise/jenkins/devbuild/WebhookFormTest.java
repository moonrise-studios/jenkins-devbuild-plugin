package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.moonrise.jenkins.devbuild.publish.WebhookTester;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The "Test webhook" button and the credential dropdown, including their permission checks.
 */
@WithJenkins
class WebhookFormTest {

    private static final String DESCRIPTOR = "descriptorByName/gg.moonrise.jenkins.devbuild.DiscordWebhook/";

    private static WebResponse post(JenkinsRule.WebClient wc, JenkinsRule r, String path, List<NameValuePair> params)
            throws Exception {
        WebRequest request = new WebRequest(r.getURL().toURI().resolve(path).toURL(), HttpMethod.POST);
        request.setRequestParameters(params);
        wc.addCrumb(request);
        return wc.getPage(request).getWebResponse();
    }

    @Test
    void testButtonSendsMessageWithPermission(JenkinsRule r) throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        FreeStyleProject p = r.createFreeStyleProject("p");
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE).everywhere().to("dev")
                .grant(Jenkins.READ, Item.READ).everywhere().to("viewer"));
        try (FakeDiscord discord = new FakeDiscord()) {
            List<NameValuePair> params = List.of(new NameValuePair("webhookUrl", discord.url()),
                    new NameValuePair("threadName", "Forum post"), new NameValuePair("appliedTags", "111111111111111111"));

            try (JenkinsRule.WebClient wc = r.createWebClient().login("admin")) {
                String body = post(wc, r, DESCRIPTOR + "testWebhook", params).getContentAsString();
                assertTrue(body.contains("Test message sent"), body);
                assertFalse(body.contains(FakeDiscord.TOKEN), body);
            }
            FakeDiscord.Request request = discord.only();
            assertEquals("DevBuild webhook test", request.payload().getString("thread_name"));
            assertEquals("DevBuild webhook test", request.embed().getString("title"));

            try (JenkinsRule.WebClient wc = r.createWebClient().login("dev")) {
                String body = post(wc, r, "job/p/" + DESCRIPTOR + "testWebhook", params).getContentAsString();
                assertTrue(body.contains("Test message sent"), body);
            }
            assertEquals(2, discord.requests().size());

            try (JenkinsRule.WebClient wc = r.createWebClient().login("dev")) {
                wc.setThrowExceptionOnFailingStatusCode(false);
                assertEquals(403, post(wc, r, DESCRIPTOR + "testWebhook", params).getStatusCode(),
                        "global test needs Administer");
            }
            try (JenkinsRule.WebClient wc = r.createWebClient().login("viewer")) {
                wc.setThrowExceptionOnFailingStatusCode(false);
                assertEquals(403, post(wc, r, "job/p/" + DESCRIPTOR + "testWebhook", params).getStatusCode(),
                        "item test needs Configure");
            }
            assertEquals(2, discord.requests().size());
        }
    }

    @Test
    void testButtonReportsDiscordErrors(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            discord.enqueue(401, "{\"message\":\"Invalid Webhook Token\",\"code\":50027}");
            DiscordWebhook webhook = new DiscordWebhook();
            webhook.setWebhookUrl(Secret.fromString(discord.url()));
            FormValidation result = WebhookTester.test(webhook, null);
            assertEquals(FormValidation.Kind.ERROR, result.kind);
            assertTrue(result.getMessage().contains("HTTP 401"), result.getMessage());
            assertFalse(result.getMessage().contains(FakeDiscord.TOKEN), result.getMessage());
        }
    }

    @Test
    void credentialsDropdownListsSecretTextCredentials(JenkinsRule r) throws Exception {
        try (FakeDiscord discord = new FakeDiscord()) {
            TestSupport.registerWebhook(discord);
            DiscordWebhook.DescriptorImpl descriptor = r.jenkins.getDescriptorByType(DiscordWebhook.DescriptorImpl.class);
            ListBoxModel items = descriptor.doFillCredentialsIdItems(null, "");
            assertTrue(items.stream().anyMatch(option -> option.value.equals("discord-devbuilds")),
                    items.stream().map(option -> option.value).toList().toString());
        }
    }

    @Test
    void validation(JenkinsRule r) {
        DiscordWebhook.DescriptorImpl descriptor = r.jenkins.getDescriptorByType(DiscordWebhook.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckThreadId("1234567890123456789").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckThreadId("general").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckAppliedTags("1, x").kind);
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckWebhookUrl(null, "https://discord.com/api/webhooks/1234567/abc").kind);
        assertEquals(FormValidation.Kind.WARNING, descriptor.doCheckWebhookUrl(null, "https://example.com/hook").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckWebhookUrl(null, "nope").kind);
        EmbedSettings.DescriptorImpl embed = r.jenkins.getDescriptorByType(EmbedSettings.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.OK, embed.doCheckSuccessColor("#2ECC71").kind);
        assertEquals(FormValidation.Kind.ERROR, embed.doCheckSuccessColor("green").kind);
        NotifyConditions.DescriptorImpl conditions = r.jenkins.getDescriptorByType(NotifyConditions.DescriptorImpl.class);
        assertEquals(FormValidation.Kind.ERROR, conditions.doCheckBranchPattern("([").kind);
    }
}
