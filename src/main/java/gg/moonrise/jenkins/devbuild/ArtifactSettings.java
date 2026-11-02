package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.discord.DiscordLimits;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Which files are uploaded to Discord and how they are split across messages.
 */
public class ArtifactSettings implements Describable<ArtifactSettings> {

    public static final String DEFAULT_INCLUDES = "**/*.jar";
    public static final String DEFAULT_EXCLUDES = "**/*-sources.jar, **/*-javadoc.jar";
    public static final int DEFAULT_MAX_FILES = 10;
    public static final int DEFAULT_MAX_UPLOAD_SIZE_MB = 10;
    private static final int MAX_REQUEST_MB = (int) (DiscordLimits.MAX_REQUEST_BYTES / (1024 * 1024));

    private boolean enabled = true;
    private String includes = DEFAULT_INCLUDES;
    private String excludes = DEFAULT_EXCLUDES;
    private ArtifactSource source = ArtifactSource.AUTO;
    private int maxFiles = DEFAULT_MAX_FILES;
    private int maxUploadSizeMb = DEFAULT_MAX_UPLOAD_SIZE_MB;
    private boolean onlyOnSuccess = true;
    private boolean attachOnUnstable;
    private OversizePolicy oversizePolicy = OversizePolicy.LINK;
    private FilePlacement placement = FilePlacement.AFTER_EMBED;

    @DataBoundConstructor
    public ArtifactSettings() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    @DataBoundSetter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NonNull
    public String getIncludes() {
        return TextUtils.nullToEmpty(includes);
    }

    @DataBoundSetter
    public void setIncludes(@CheckForNull String includes) {
        this.includes = TextUtils.nullToEmpty(includes).trim();
    }

    @NonNull
    public String getExcludes() {
        return TextUtils.nullToEmpty(excludes);
    }

    @DataBoundSetter
    public void setExcludes(@CheckForNull String excludes) {
        this.excludes = TextUtils.nullToEmpty(excludes).trim();
    }

    @NonNull
    public ArtifactSource getSource() {
        return source == null ? ArtifactSource.AUTO : source;
    }

    @DataBoundSetter
    public void setSource(@CheckForNull ArtifactSource source) {
        this.source = source == null ? ArtifactSource.AUTO : source;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    @DataBoundSetter
    public void setMaxFiles(int maxFiles) {
        this.maxFiles = Math.max(0, maxFiles);
    }

    public int getMaxUploadSizeMb() {
        return maxUploadSizeMb;
    }

    @DataBoundSetter
    public void setMaxUploadSizeMb(int maxUploadSizeMb) {
        this.maxUploadSizeMb = Math.max(1, Math.min(MAX_REQUEST_MB, maxUploadSizeMb));
    }

    public boolean isOnlyOnSuccess() {
        return onlyOnSuccess;
    }

    @DataBoundSetter
    public void setOnlyOnSuccess(boolean onlyOnSuccess) {
        this.onlyOnSuccess = onlyOnSuccess;
    }

    public boolean isAttachOnUnstable() {
        return attachOnUnstable;
    }

    @DataBoundSetter
    public void setAttachOnUnstable(boolean attachOnUnstable) {
        this.attachOnUnstable = attachOnUnstable;
    }

    @NonNull
    public OversizePolicy getOversizePolicy() {
        return oversizePolicy == null ? OversizePolicy.LINK : oversizePolicy;
    }

    @DataBoundSetter
    public void setOversizePolicy(@CheckForNull OversizePolicy oversizePolicy) {
        this.oversizePolicy = oversizePolicy == null ? OversizePolicy.LINK : oversizePolicy;
    }

    /**
     * Where the files go relative to the embed. Configurations saved before this option existed load as
     * {@link FilePlacement#AFTER_EMBED}.
     */
    @NonNull
    public FilePlacement getPlacement() {
        return placement == null ? FilePlacement.AFTER_EMBED : placement;
    }

    @DataBoundSetter
    public void setPlacement(@CheckForNull FilePlacement placement) {
        this.placement = placement == null ? FilePlacement.AFTER_EMBED : placement;
    }

    /**
     * Upload budget per message in bytes.
     */
    public long maxUploadBytes() {
        return (long) maxUploadSizeMb * 1024L * 1024L;
    }

    /**
     * Whether files are uploaded for a build with this result.
     */
    public boolean attachesFor(@NonNull Result result) {
        if (!enabled) {
            return false;
        }
        if (!onlyOnSuccess || result == Result.SUCCESS) {
            return true;
        }
        return result == Result.UNSTABLE && attachOnUnstable;
    }

    @Extension
    @Symbol("devBuildArtifacts")
    public static class DescriptorImpl extends Descriptor<ArtifactSettings> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Attachments";
        }

        public FormValidation doCheckIncludes(@QueryParameter String value) {
            return TextUtils.isBlank(value)
                    ? FormValidation.warning("No pattern: no files will be attached")
                    : FormValidation.ok();
        }

        public FormValidation doCheckMaxFiles(@QueryParameter int value) {
            if (value < 0) {
                return FormValidation.error("Must be 0 or more");
            }
            if (value > DiscordLimits.ATTACHMENTS_PER_MESSAGE) {
                return FormValidation.ok("Files beyond " + DiscordLimits.ATTACHMENTS_PER_MESSAGE
                        + " are sent in follow-up messages");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMaxUploadSizeMb(@QueryParameter int value) {
            if (value < 1 || value > MAX_REQUEST_MB) {
                return FormValidation.error("Must be between 1 and " + MAX_REQUEST_MB);
            }
            if (value > DEFAULT_MAX_UPLOAD_SIZE_MB) {
                return FormValidation.warning("Unboosted servers accept about 10 MiB per message; "
                        + "only raise this for boosted servers");
            }
            return FormValidation.ok();
        }
    }
}
