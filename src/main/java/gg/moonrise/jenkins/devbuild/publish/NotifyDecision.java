package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.NotifyConditions;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.model.Result;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Whether a build should be announced, according to {@link NotifyConditions}.
 */
public sealed interface NotifyDecision {

    /** Send the notification. */
    record Send() implements NotifyDecision {
    }

    /** Do not send; {@code reason} is logged. */
    record Skip(@NonNull String reason) implements NotifyDecision {
    }

    /**
     * Evaluates the conditions.
     *
     * @param result         result of this build
     * @param previousResult result of the previous completed build, or {@code null} for the first build
     * @param branch         {@code BRANCH_NAME}, or {@code null} when the job is not a branch job
     */
    @NonNull
    static NotifyDecision evaluate(@NonNull NotifyConditions conditions, @NonNull Result result,
                                   @CheckForNull Result previousResult, @CheckForNull String branch) {
        String pattern = conditions.getBranchPattern();
        if (!TextUtils.isBlank(branch) && !pattern.isEmpty()) {
            try {
                if (!Pattern.compile(pattern).matcher(branch).matches()) {
                    return new Skip("branch '" + branch + "' does not match branchPattern '" + pattern + "'");
                }
            } catch (PatternSyntaxException e) {
                return new Skip("branchPattern '" + pattern + "' is not a valid regular expression");
            }
        }
        if (result == Result.SUCCESS) {
            if (conditions.isNotifyOnSuccess()) {
                return new Send();
            }
            boolean backToNormal = previousResult != null && previousResult != Result.SUCCESS;
            if (backToNormal && conditions.isNotifyOnBackToNormal()) {
                return new Send();
            }
            return new Skip("notifyOnSuccess is off" + (backToNormal ? " and notifyOnBackToNormal is off" : ""));
        }
        if (result == Result.UNSTABLE) {
            return conditions.isNotifyOnUnstable() ? new Send() : new Skip("notifyOnUnstable is off");
        }
        if (result == Result.FAILURE) {
            return conditions.isNotifyOnFailure() ? new Send() : new Skip("notifyOnFailure is off");
        }
        if (result == Result.ABORTED) {
            return conditions.isNotifyOnAborted() ? new Send() : new Skip("notifyOnAborted is off");
        }
        return conditions.isNotifyOnNotBuilt() ? new Send() : new Skip("notifyOnNotBuilt is off");
    }
}
