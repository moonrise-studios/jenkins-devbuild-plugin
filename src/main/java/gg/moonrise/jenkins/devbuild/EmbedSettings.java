package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.ColorUtils;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Settings for the build embed. Text values are templates expanded against the build.
 */
public class EmbedSettings implements Describable<EmbedSettings> {

    public static final String DEFAULT_TITLE = "${PROJECT_NAME} #${BUILD_NUMBER}";
    public static final String DEFAULT_URL = "${BUILD_URL}";
    public static final String DEFAULT_SUCCESS_COLOR = "#2ECC71";
    public static final String DEFAULT_UNSTABLE_COLOR = "#F1C40F";
    public static final String DEFAULT_FAILURE_COLOR = "#E74C3C";
    public static final String DEFAULT_ABORTED_COLOR = "#95A5A6";
    public static final String DEFAULT_NOT_BUILT_COLOR = "#95A5A6";
    public static final int DEFAULT_MAX_CHANGES = 5;
    /** The Moonrise Studios logo, shown as the thumbnail unless another URL or a blank value is configured. */
    public static final String DEFAULT_THUMBNAIL_URL =
            "https://cdn.moonrise.gg/api/cdn/download/images/Moonrise%20Studios%20Logo%20%281%29.png";

    private boolean enabled = true;
    private String title = DEFAULT_TITLE;
    private String description = "";
    private String url = DEFAULT_URL;
    private String authorName = "";
    private String authorUrl = "";
    private String authorIconUrl = "";
    private String thumbnailUrl = DEFAULT_THUMBNAIL_URL;
    private String imageUrl = "";
    private String footerText = "";
    private String footerIconUrl = "";
    private boolean timestamp = true;
    private String successColor = DEFAULT_SUCCESS_COLOR;
    private String unstableColor = DEFAULT_UNSTABLE_COLOR;
    private String failureColor = DEFAULT_FAILURE_COLOR;
    private String abortedColor = DEFAULT_ABORTED_COLOR;
    private String notBuiltColor = DEFAULT_NOT_BUILT_COLOR;
    private boolean showResult = true;
    private boolean showDuration = true;
    private boolean showBranch = true;
    private boolean showCommit = true;
    private boolean showChanges = true;
    private int maxChanges = DEFAULT_MAX_CHANGES;
    private boolean showArtifacts = true;
    private List<EmbedField> fields = new ArrayList<>();

    @DataBoundConstructor
    public EmbedSettings() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NonNull
    public String getTitle() {
        return TextUtils.nullToEmpty(title);
    }

    @DataBoundSetter
    public void setTitle(@CheckForNull String title) {
        this.title = TextUtils.nullToEmpty(title);
    }

    @NonNull
    public String getDescription() {
        return TextUtils.nullToEmpty(description);
    }

    @DataBoundSetter
    public void setDescription(@CheckForNull String description) {
        this.description = TextUtils.nullToEmpty(description);
    }

    @NonNull
    public String getUrl() {
        return TextUtils.nullToEmpty(url);
    }

    @DataBoundSetter
    public void setUrl(@CheckForNull String url) {
        this.url = TextUtils.nullToEmpty(url).trim();
    }

    @NonNull
    public String getAuthorName() {
        return TextUtils.nullToEmpty(authorName);
    }

    @DataBoundSetter
    public void setAuthorName(@CheckForNull String authorName) {
        this.authorName = TextUtils.nullToEmpty(authorName);
    }

    @NonNull
    public String getAuthorUrl() {
        return TextUtils.nullToEmpty(authorUrl);
    }

    @DataBoundSetter
    public void setAuthorUrl(@CheckForNull String authorUrl) {
        this.authorUrl = TextUtils.nullToEmpty(authorUrl).trim();
    }

    @NonNull
    public String getAuthorIconUrl() {
        return TextUtils.nullToEmpty(authorIconUrl);
    }

    @DataBoundSetter
    public void setAuthorIconUrl(@CheckForNull String authorIconUrl) {
        this.authorIconUrl = TextUtils.nullToEmpty(authorIconUrl).trim();
    }

    /**
     * The thumbnail URL template. Empty means no thumbnail; configurations saved before the default existed (field
     * absent, so {@code null}) get {@link #DEFAULT_THUMBNAIL_URL}.
     */
    @NonNull
    public String getThumbnailUrl() {
        return thumbnailUrl == null ? DEFAULT_THUMBNAIL_URL : thumbnailUrl;
    }

    @DataBoundSetter
    public void setThumbnailUrl(@CheckForNull String thumbnailUrl) {
        this.thumbnailUrl = TextUtils.nullToEmpty(thumbnailUrl).trim();
    }

    @NonNull
    public String getImageUrl() {
        return TextUtils.nullToEmpty(imageUrl);
    }

    @DataBoundSetter
    public void setImageUrl(@CheckForNull String imageUrl) {
        this.imageUrl = TextUtils.nullToEmpty(imageUrl).trim();
    }

    @NonNull
    public String getFooterText() {
        return TextUtils.nullToEmpty(footerText);
    }

    @DataBoundSetter
    public void setFooterText(@CheckForNull String footerText) {
        this.footerText = TextUtils.nullToEmpty(footerText);
    }

    @NonNull
    public String getFooterIconUrl() {
        return TextUtils.nullToEmpty(footerIconUrl);
    }

    @DataBoundSetter
    public void setFooterIconUrl(@CheckForNull String footerIconUrl) {
        this.footerIconUrl = TextUtils.nullToEmpty(footerIconUrl).trim();
    }

    public boolean isTimestamp() {
        return timestamp;
    }

    @DataBoundSetter
    public void setTimestamp(boolean timestamp) {
        this.timestamp = timestamp;
    }

    @NonNull
    public String getSuccessColor() {
        return colorOrDefault(successColor, DEFAULT_SUCCESS_COLOR);
    }

    @DataBoundSetter
    public void setSuccessColor(@CheckForNull String successColor) {
        this.successColor = TextUtils.trimToNull(successColor);
    }

    @NonNull
    public String getUnstableColor() {
        return colorOrDefault(unstableColor, DEFAULT_UNSTABLE_COLOR);
    }

    @DataBoundSetter
    public void setUnstableColor(@CheckForNull String unstableColor) {
        this.unstableColor = TextUtils.trimToNull(unstableColor);
    }

    @NonNull
    public String getFailureColor() {
        return colorOrDefault(failureColor, DEFAULT_FAILURE_COLOR);
    }

    @DataBoundSetter
    public void setFailureColor(@CheckForNull String failureColor) {
        this.failureColor = TextUtils.trimToNull(failureColor);
    }

    @NonNull
    public String getAbortedColor() {
        return colorOrDefault(abortedColor, DEFAULT_ABORTED_COLOR);
    }

    @DataBoundSetter
    public void setAbortedColor(@CheckForNull String abortedColor) {
        this.abortedColor = TextUtils.trimToNull(abortedColor);
    }

    @NonNull
    public String getNotBuiltColor() {
        return colorOrDefault(notBuiltColor, DEFAULT_NOT_BUILT_COLOR);
    }

    @DataBoundSetter
    public void setNotBuiltColor(@CheckForNull String notBuiltColor) {
        this.notBuiltColor = TextUtils.trimToNull(notBuiltColor);
    }

    public boolean isShowResult() {
        return showResult;
    }

    @DataBoundSetter
    public void setShowResult(boolean showResult) {
        this.showResult = showResult;
    }

    public boolean isShowDuration() {
        return showDuration;
    }

    @DataBoundSetter
    public void setShowDuration(boolean showDuration) {
        this.showDuration = showDuration;
    }

    public boolean isShowBranch() {
        return showBranch;
    }

    @DataBoundSetter
    public void setShowBranch(boolean showBranch) {
        this.showBranch = showBranch;
    }

    public boolean isShowCommit() {
        return showCommit;
    }

    @DataBoundSetter
    public void setShowCommit(boolean showCommit) {
        this.showCommit = showCommit;
    }

    public boolean isShowChanges() {
        return showChanges;
    }

    @DataBoundSetter
    public void setShowChanges(boolean showChanges) {
        this.showChanges = showChanges;
    }

    public int getMaxChanges() {
        return maxChanges;
    }

    @DataBoundSetter
    public void setMaxChanges(int maxChanges) {
        this.maxChanges = Math.max(0, maxChanges);
    }

    public boolean isShowArtifacts() {
        return showArtifacts;
    }

    @DataBoundSetter
    public void setShowArtifacts(boolean showArtifacts) {
        this.showArtifacts = showArtifacts;
    }

    @NonNull
    public List<EmbedField> getFields() {
        return fields == null ? List.of() : List.copyOf(fields);
    }

    @DataBoundSetter
    public void setFields(@CheckForNull List<EmbedField> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    /**
     * The embed colour for a build result.
     */
    public int colorFor(@NonNull Result result) {
        String hex;
        if (result == Result.SUCCESS) {
            hex = getSuccessColor();
        } else if (result == Result.UNSTABLE) {
            hex = getUnstableColor();
        } else if (result == Result.FAILURE) {
            hex = getFailureColor();
        } else if (result == Result.ABORTED) {
            hex = getAbortedColor();
        } else {
            hex = getNotBuiltColor();
        }
        return ColorUtils.parseHex(hex).orElse(0);
    }

    private static String colorOrDefault(@CheckForNull String value, String fallback) {
        return value == null ? fallback : value;
    }

    @Extension
    @Symbol("devBuildEmbed")
    public static class DescriptorImpl extends Descriptor<EmbedSettings> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Embed";
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        public FormValidation doCheckAuthorUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        public FormValidation doCheckAuthorIconUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        @NonNull
        public String getDefaultThumbnailUrl() {
            return DEFAULT_THUMBNAIL_URL;
        }

        public FormValidation doCheckThumbnailUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        public FormValidation doCheckImageUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        public FormValidation doCheckFooterIconUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        public FormValidation doCheckSuccessColor(@QueryParameter String value) {
            return DevBuildForms.checkHexColor(value);
        }

        public FormValidation doCheckUnstableColor(@QueryParameter String value) {
            return DevBuildForms.checkHexColor(value);
        }

        public FormValidation doCheckFailureColor(@QueryParameter String value) {
            return DevBuildForms.checkHexColor(value);
        }

        public FormValidation doCheckAbortedColor(@QueryParameter String value) {
            return DevBuildForms.checkHexColor(value);
        }

        public FormValidation doCheckNotBuiltColor(@QueryParameter String value) {
            return DevBuildForms.checkHexColor(value);
        }

        public FormValidation doCheckMaxChanges(@QueryParameter int value) {
            if (value < 0) {
                return FormValidation.error("Must be 0 or more");
            }
            return FormValidation.ok();
        }
    }
}
