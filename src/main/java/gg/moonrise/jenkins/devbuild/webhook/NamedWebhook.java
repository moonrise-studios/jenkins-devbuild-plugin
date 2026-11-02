package gg.moonrise.jenkins.devbuild.webhook;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A webhook defined once in the global configuration and referenced by name from jobs, folders and steps.
 */
public class NamedWebhook implements Describable<NamedWebhook> {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private final String name;
    private final DiscordWebhook webhook;

    @DataBoundConstructor
    public NamedWebhook(String name, DiscordWebhook webhook) {
        this.name = TextUtils.nullToEmpty(name).trim();
        this.webhook = webhook == null ? new DiscordWebhook() : webhook;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public DiscordWebhook getWebhook() {
        return webhook;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NamedWebhook other)) {
            return false;
        }
        return name.equals(other.name) && webhook.equals(other.webhook);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, webhook);
    }

    @Override
    public String toString() {
        return "NamedWebhook[" + name + ", " + webhook + "]";
    }

    @Extension
    @Symbol("namedWebhook")
    public static class DescriptorImpl extends Descriptor<NamedWebhook> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Named Discord webhook";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            if (TextUtils.isBlank(value)) {
                return FormValidation.error("A name is required");
            }
            if (!NAME.matcher(value.trim()).matches()) {
                return FormValidation.error("Use letters, digits, '.', '_' and '-' only");
            }
            return FormValidation.ok();
        }
    }
}
