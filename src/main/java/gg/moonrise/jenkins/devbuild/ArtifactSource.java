package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Where the files to attach are taken from.
 */
public enum ArtifactSource {
    /** Archived artifacts matching the patterns; workspace files if none match and a workspace is available. */
    AUTO("Archived artifacts, falling back to the workspace"),
    /** Only archived artifacts. */
    ARCHIVED("Archived artifacts only"),
    /** Only files in the workspace. Requires the step to run inside {@code node}. */
    WORKSPACE("Workspace files only");

    private final String displayName;

    ArtifactSource(String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }
}
