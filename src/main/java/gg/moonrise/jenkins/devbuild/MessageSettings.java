package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Message level settings: the text above the embed, the sender identity and mention handling.
 */
public class MessageSettings implements Describable<MessageSettings> {

    /** The Jenkins icon, used as the webhook avatar unless another URL or a blank value is configured. */
    public static final String DEFAULT_AVATAR_URL = "https://cdn.moonrise.gg/api/cdn/download/images/jenkins-icon.png";

    private String content = "";
    private String username = "";
    private String avatarUrl = DEFAULT_AVATAR_URL;
    private boolean tts;
    private boolean suppressNotifications;
    private boolean mentionUsers = true;
    private boolean mentionRoles = true;
    private boolean mentionEveryone;

    @DataBoundConstructor
    public MessageSettings() {
    }

    @NonNull
    public String getContent() {
        return TextUtils.nullToEmpty(content);
    }

    @DataBoundSetter
    public void setContent(@CheckForNull String content) {
        this.content = TextUtils.nullToEmpty(content);
    }

    @NonNull
    public String getUsername() {
        return TextUtils.nullToEmpty(username);
    }

    @DataBoundSetter
    public void setUsername(@CheckForNull String username) {
        this.username = TextUtils.nullToEmpty(username).trim();
    }

    /**
     * The avatar URL template. Empty means the webhook's own avatar; configurations saved before the default existed
     * (field absent, so {@code null}) get {@link #DEFAULT_AVATAR_URL}.
     */
    @NonNull
    public String getAvatarUrl() {
        return avatarUrl == null ? DEFAULT_AVATAR_URL : avatarUrl;
    }

    @DataBoundSetter
    public void setAvatarUrl(@CheckForNull String avatarUrl) {
        this.avatarUrl = TextUtils.nullToEmpty(avatarUrl).trim();
    }

    public boolean isTts() {
        return tts;
    }

    @DataBoundSetter
    public void setTts(boolean tts) {
        this.tts = tts;
    }

    public boolean isSuppressNotifications() {
        return suppressNotifications;
    }

    @DataBoundSetter
    public void setSuppressNotifications(boolean suppressNotifications) {
        this.suppressNotifications = suppressNotifications;
    }

    public boolean isMentionUsers() {
        return mentionUsers;
    }

    @DataBoundSetter
    public void setMentionUsers(boolean mentionUsers) {
        this.mentionUsers = mentionUsers;
    }

    public boolean isMentionRoles() {
        return mentionRoles;
    }

    @DataBoundSetter
    public void setMentionRoles(boolean mentionRoles) {
        this.mentionRoles = mentionRoles;
    }

    public boolean isMentionEveryone() {
        return mentionEveryone;
    }

    @DataBoundSetter
    public void setMentionEveryone(boolean mentionEveryone) {
        this.mentionEveryone = mentionEveryone;
    }

    /**
     * The {@code allowed_mentions.parse} list for these settings.
     */
    @NonNull
    public List<String> allowedMentionTypes() {
        List<String> parse = new ArrayList<>();
        if (mentionUsers) {
            parse.add("users");
        }
        if (mentionRoles) {
            parse.add("roles");
        }
        if (mentionEveryone) {
            parse.add("everyone");
        }
        return parse;
    }

    @Extension
    @Symbol("devBuildMessage")
    public static class DescriptorImpl extends Descriptor<MessageSettings> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Message";
        }

        @NonNull
        public String getDefaultAvatarUrl() {
            return DEFAULT_AVATAR_URL;
        }

        public FormValidation doCheckAvatarUrl(@QueryParameter String value) {
            return DevBuildForms.checkTemplateUrl(value);
        }

        public FormValidation doCheckContent(@QueryParameter String value) {
            if (value != null && value.length() > 2000) {
                return FormValidation.warning("Discord allows 2000 characters; longer text is cut off");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckUsername(@QueryParameter String value) {
            if (value != null && value.length() > 80) {
                return FormValidation.warning("Discord allows 80 characters; longer names are cut off");
            }
            if (value != null && value.toLowerCase(Locale.ROOT).contains("discord")) {
                return FormValidation.error("Discord rejects usernames that contain \"discord\"");
            }
            return FormValidation.ok();
        }
    }
}
