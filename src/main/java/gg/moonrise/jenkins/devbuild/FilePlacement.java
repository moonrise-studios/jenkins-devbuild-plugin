package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Where uploaded files go relative to the embed. Discord always renders a classic message's attachments above its
 * embeds, so with a classic embed the files only appear below it when they are sent in a separate message.
 * {@link #IN_EMBED} avoids that by laying the build card out with Discord components, which can hold the files.
 */
public enum FilePlacement {
    /**
     * Post one message that lays the embed out with Discord components (Components V2) and shows the first files
     * inside the card. Further files follow in classic messages.
     */
    IN_EMBED("Inside the embed, in one message (Discord components)"),
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
