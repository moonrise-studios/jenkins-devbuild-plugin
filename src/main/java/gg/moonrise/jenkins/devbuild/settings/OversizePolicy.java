package gg.moonrise.jenkins.devbuild.settings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * What to do with a file larger than the per-message upload budget.
 */
public enum OversizePolicy {
    /** List the file in the embed with a link to the Jenkins artifact instead of uploading it. */
    LINK("Link to the Jenkins artifact instead"),
    /** Leave the file out. */
    SKIP("Skip the file"),
    /** Fail the step. When publishing automatically, the notification is not sent and the error is logged. */
    FAIL("Fail");

    private final String displayName;

    OversizePolicy(String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }
}
