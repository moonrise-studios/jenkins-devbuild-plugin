package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Result of a publish attempt that did not throw.
 */
public sealed interface PublishOutcome {

    /**
     * Messages were posted.
     *
     * @param messageIds ids of the created messages, main message first
     * @param uploaded   number of files uploaded
     */
    record Sent(@NonNull List<String> messageIds, int uploaded) implements PublishOutcome {
        public Sent {
            messageIds = List.copyOf(messageIds);
        }
    }

    /**
     * The notify conditions ruled the build out.
     */
    record Skipped(@NonNull String reason) implements PublishOutcome {
    }
}
