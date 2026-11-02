package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;

/**
 * A file uploaded with a webhook message. The content is streamed from {@link Source} on every attempt so a retried
 * request does not need to keep the file in memory.
 *
 * @param filename name shown in Discord
 * @param size     exact size in bytes, used for the request's Content-Length
 * @param source   opens a fresh stream over the content
 */
public record DiscordAttachment(@NonNull String filename, long size, @NonNull Source source) {

    /**
     * Opens the content of an attachment. Implementations read from the controller or an agent.
     */
    @FunctionalInterface
    public interface Source {
        InputStream open() throws IOException, InterruptedException;
    }
}
