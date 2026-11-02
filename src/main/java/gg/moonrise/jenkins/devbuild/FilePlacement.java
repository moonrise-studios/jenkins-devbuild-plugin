package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Where uploaded files go relative to the embed. Discord always renders a message's attachments above its embeds, so
 * the files only appear below the embed when they are sent in a separate message.
 */
public enum FilePlacement {
    /** Send the embed first, then the files in follow-up messages, so they show below the embed. */
    AFTER_EMBED("In a message after the embed"),
    /** Attach the first files to the embed message; Discord shows them above the embed. */
    WITH_EMBED("Attached to the embed message (shown above the embed)");

    private final String displayName;

    FilePlacement(String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }
}
