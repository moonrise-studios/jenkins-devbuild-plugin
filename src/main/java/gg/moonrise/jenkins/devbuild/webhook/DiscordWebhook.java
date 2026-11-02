package gg.moonrise.jenkins.devbuild.webhook;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.discord.WebhookUrls;
import gg.moonrise.jenkins.devbuild.publish.WebhookCredentials;
import gg.moonrise.jenkins.devbuild.publish.WebhookTester;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Where a message goes: the webhook URL (preferably from a Secret text credential) and the optional thread or forum
 * post inside the webhook's channel.
 */
public class DiscordWebhook implements Describable<DiscordWebhook> {

    private static final Pattern SNOWFLAKE = Pattern.compile("^\\d{15,25}$");

    private String credentialsId;
    private Secret webhookUrl;
    private String threadId;
    private String threadName;
    private String appliedTags;

    @DataBoundConstructor
    public DiscordWebhook() {
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = TextUtils.trimToNull(credentialsId);
    }

    @CheckForNull
    public Secret getWebhookUrl() {
        return webhookUrl;
    }

    @DataBoundSetter
    public void setWebhookUrl(@CheckForNull Secret webhookUrl) {
        this.webhookUrl = webhookUrl == null || webhookUrl.getPlainText().isBlank() ? null : webhookUrl;
    }

    @CheckForNull
    public String getThreadId() {
        return threadId;
    }

    @DataBoundSetter
    public void setThreadId(@CheckForNull String threadId) {
        this.threadId = TextUtils.trimToNull(threadId);
    }

    @CheckForNull
    public String getThreadName() {
        return threadName;
    }

    @DataBoundSetter
    public void setThreadName(@CheckForNull String threadName) {
        this.threadName = TextUtils.trimToNull(threadName);
    }

    @CheckForNull
    public String getAppliedTags() {
        return appliedTags;
    }

    @DataBoundSetter
    public void setAppliedTags(@CheckForNull String appliedTags) {
        this.appliedTags = TextUtils.trimToNull(appliedTags);
    }

    /**
     * Forum tag ids from {@link #getAppliedTags()}.
     */
    @NonNull
    public List<String> appliedTagList() {
        return TextUtils.splitCommaList(appliedTags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DiscordWebhook other)) {
            return false;
        }
        return Objects.equals(credentialsId, other.credentialsId)
                && Objects.equals(webhookUrl, other.webhookUrl)
                && Objects.equals(threadId, other.threadId)
                && Objects.equals(threadName, other.threadName)
                && Objects.equals(appliedTags, other.appliedTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(credentialsId, webhookUrl, threadId, threadName, appliedTags);
    }

    @Override
    public String toString() {
        return "DiscordWebhook[credentialsId=" + credentialsId + ", webhookUrl="
                + (webhookUrl == null ? "none" : WebhookUrls.mask(webhookUrl.getPlainText()))
                + ", threadId=" + threadId + ", threadName=" + threadName + ", appliedTags=" + appliedTags + "]";
    }

    @Extension
    @Symbol("discordWebhook")
    public static class DescriptorImpl extends Descriptor<DiscordWebhook> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Discord webhook";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (!canUseCredentials(item)) {
                return result.includeCurrentValue(credentialsId);
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            item instanceof Queue.Task task ? Tasks.getAuthenticationOf2(task) : ACL.SYSTEM2,
                            item,
                            StringCredentials.class,
                            List.of(),
                            CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }

        @POST
        public FormValidation doCheckCredentialsId(@AncestorInPath Item item, @QueryParameter String value,
                                                   @QueryParameter String webhookUrl) {
            if (!canUseCredentials(item)) {
                return FormValidation.ok();
            }
            if (TextUtils.isBlank(value)) {
                return isBlankSecret(webhookUrl)
                        ? FormValidation.warning("Select a Secret text credential or enter a webhook URL, "
                                + "otherwise this webhook inherits nothing and cannot be used.")
                        : FormValidation.ok();
            }
            if (value.startsWith("${") && value.endsWith("}")) {
                return FormValidation.warning("Cannot validate expressions");
            }
            if (WebhookCredentials.lookupForItem(value, item).isEmpty()) {
                return FormValidation.error("No Secret text credential with this id is visible here");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckWebhookUrl(@AncestorInPath Item item, @QueryParameter String value) {
            if (!canConfigure(item) || isBlankSecret(value)) {
                return FormValidation.ok();
            }
            String url = Secret.fromString(value).getPlainText().trim();
            if (WebhookUrls.isDiscordWebhookUrl(url)) {
                return FormValidation.ok();
            }
            if (WebhookUrls.isHttpUrl(url)) {
                return FormValidation.warning("This does not look like a Discord webhook URL "
                        + "(https://discord.com/api/webhooks/<id>/<token>)");
            }
            return FormValidation.error("Not a valid URL");
        }

        public FormValidation doCheckThreadId(@QueryParameter String value) {
            if (TextUtils.isBlank(value) || SNOWFLAKE.matcher(value.trim()).matches()) {
                return FormValidation.ok();
            }
            return FormValidation.error("A thread id is a number such as 1234567890123456789 "
                    + "(right click the thread with Developer Mode on, then Copy Thread ID)");
        }

        public FormValidation doCheckAppliedTags(@QueryParameter String value) {
            List<String> tags = TextUtils.splitCommaList(value);
            for (String tag : tags) {
                if (!SNOWFLAKE.matcher(tag).matches()) {
                    return FormValidation.error("Not a tag id: " + tag);
                }
            }
            if (tags.size() > 5) {
                return FormValidation.warning("Discord applies at most 5 tags to a forum post");
            }
            return FormValidation.ok();
        }

        /**
         * Sends a test message with the values currently in the form.
         */
        @POST
        public FormValidation doTestWebhook(@AncestorInPath Item item,
                                            @QueryParameter String credentialsId,
                                            @QueryParameter String webhookUrl,
                                            @QueryParameter String threadId,
                                            @QueryParameter String threadName,
                                            @QueryParameter String appliedTags) {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                item.checkPermission(Item.CONFIGURE);
            }
            DiscordWebhook webhook = new DiscordWebhook();
            webhook.setCredentialsId(credentialsId);
            webhook.setWebhookUrl(isBlankSecret(webhookUrl) ? null : Secret.fromString(webhookUrl));
            webhook.setThreadId(threadId);
            webhook.setThreadName(threadName);
            webhook.setAppliedTags(appliedTags);
            return WebhookTester.test(webhook, item);
        }

        private static boolean isBlankSecret(@CheckForNull String value) {
            return value == null || Secret.fromString(value).getPlainText().isBlank();
        }

        private static boolean canConfigure(@CheckForNull Item item) {
            return item == null
                    ? Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    : item.hasPermission(Item.CONFIGURE);
        }

        private static boolean canUseCredentials(@CheckForNull Item item) {
            if (item == null) {
                return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
            }
            return item.hasPermission(Item.EXTENDED_READ) || item.hasPermission(CredentialsProvider.USE_ITEM);
        }
    }
}
