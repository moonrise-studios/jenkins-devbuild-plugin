package gg.moonrise.jenkins.devbuild.build;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;

/**
 * Invisible marker recording that the DevBuild step or post-build action ran for a build, with the ids of the
 * Discord messages it created. The automatic publisher uses it to avoid posting twice.
 */
public class DevBuildAction extends InvisibleAction {

    private List<String> messageIds = new ArrayList<>();
    private int invocations;

    /**
     * Records one step invocation on the run, creating the action when needed.
     */
    public static void record(@NonNull Run<?, ?> run, @NonNull List<String> createdMessageIds) {
        synchronized (DevBuildAction.class) {
            DevBuildAction action = run.getAction(DevBuildAction.class);
            if (action == null) {
                action = new DevBuildAction();
                run.addAction(action);
            }
            action.add(createdMessageIds);
        }
    }

    private synchronized void add(List<String> createdMessageIds) {
        invocations++;
        if (messageIds == null) {
            messageIds = new ArrayList<>();
        }
        messageIds.addAll(createdMessageIds);
    }

    /**
     * Ids of all Discord messages posted for the build by the step or post-build action.
     */
    @NonNull
    public synchronized List<String> getMessageIds() {
        return messageIds == null ? List.of() : List.copyOf(messageIds);
    }

    /**
     * How often the step or post-build action ran for the build.
     */
    public synchronized int getInvocations() {
        return invocations;
    }
}
