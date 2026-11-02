package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A custom embed field. Name and value are templates.
 */
public class EmbedField implements Describable<EmbedField> {

    private final String name;
    private final String value;
    private final boolean inline;

    @DataBoundConstructor
    public EmbedField(String name, String value, boolean inline) {
        this.name = TextUtils.nullToEmpty(name);
        this.value = TextUtils.nullToEmpty(value);
        this.inline = inline;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    public boolean isInline() {
        return inline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbedField other)) {
            return false;
        }
        return inline == other.inline && name.equals(other.name) && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, inline);
    }

    @Extension
    @Symbol("embedField")
    public static class DescriptorImpl extends Descriptor<EmbedField> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Embed field";
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            return TextUtils.isBlank(value) ? FormValidation.error("A field name is required") : FormValidation.ok();
        }
    }
}
