package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * When a notification is sent.
 */
public class NotifyConditions implements Describable<NotifyConditions> {

    private boolean notifyOnSuccess = true;
    private boolean notifyOnUnstable = true;
    private boolean notifyOnFailure = true;
    private boolean notifyOnAborted;
    private boolean notifyOnNotBuilt;
    private boolean notifyOnBackToNormal = true;
    private String branchPattern = "";

    @DataBoundConstructor
    public NotifyConditions() {
    }

    public boolean isNotifyOnSuccess() {
        return notifyOnSuccess;
    }

    @DataBoundSetter
    public void setNotifyOnSuccess(boolean notifyOnSuccess) {
        this.notifyOnSuccess = notifyOnSuccess;
    }

    public boolean isNotifyOnUnstable() {
        return notifyOnUnstable;
    }

    @DataBoundSetter
    public void setNotifyOnUnstable(boolean notifyOnUnstable) {
        this.notifyOnUnstable = notifyOnUnstable;
    }

    public boolean isNotifyOnFailure() {
        return notifyOnFailure;
    }

    @DataBoundSetter
    public void setNotifyOnFailure(boolean notifyOnFailure) {
        this.notifyOnFailure = notifyOnFailure;
    }

    public boolean isNotifyOnAborted() {
        return notifyOnAborted;
    }

    @DataBoundSetter
    public void setNotifyOnAborted(boolean notifyOnAborted) {
        this.notifyOnAborted = notifyOnAborted;
    }

    public boolean isNotifyOnNotBuilt() {
        return notifyOnNotBuilt;
    }

    @DataBoundSetter
    public void setNotifyOnNotBuilt(boolean notifyOnNotBuilt) {
        this.notifyOnNotBuilt = notifyOnNotBuilt;
    }

    public boolean isNotifyOnBackToNormal() {
        return notifyOnBackToNormal;
    }

    @DataBoundSetter
    public void setNotifyOnBackToNormal(boolean notifyOnBackToNormal) {
        this.notifyOnBackToNormal = notifyOnBackToNormal;
    }

    @NonNull
    public String getBranchPattern() {
        return TextUtils.nullToEmpty(branchPattern);
    }

    @DataBoundSetter
    public void setBranchPattern(@CheckForNull String branchPattern) {
        this.branchPattern = TextUtils.nullToEmpty(branchPattern).trim();
    }

    @Extension
    @Symbol("devBuildConditions")
    public static class DescriptorImpl extends Descriptor<NotifyConditions> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Notify conditions";
        }

        public FormValidation doCheckBranchPattern(@QueryParameter String value) {
            return DevBuildForms.checkRegex(value);
        }
    }
}
