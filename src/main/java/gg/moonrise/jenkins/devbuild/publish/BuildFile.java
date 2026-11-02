package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.discord.DiscordAttachment;

/**
 * A file selected for the notification, either an archived artifact or a workspace file.
 *
 * @param name         file name without directories
 * @param relativePath path relative to the artifact root or workspace
 * @param size         size in bytes
 * @param url          Jenkins URL of the archived artifact, or {@code null} for workspace files
 * @param source       reads the content, also from an agent
 */
public record BuildFile(
        @NonNull String name,
        @NonNull String relativePath,
        long size,
        @CheckForNull String url,
        @NonNull DiscordAttachment.Source source) {

    @NonNull
    public DiscordAttachment toAttachment() {
        return new DiscordAttachment(name, size, source);
    }
}
