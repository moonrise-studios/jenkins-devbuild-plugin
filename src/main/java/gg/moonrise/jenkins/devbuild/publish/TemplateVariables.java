package gg.moonrise.jenkins.devbuild.publish;

import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.EnvVars;
import hudson.Util;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;

/**
 * Variables available to templates: the build environment plus DevBuild's own tokens (which win on conflicts).
 */
public final class TemplateVariables {

    private static final int SHORT_COMMIT_LENGTH = 7;

    private final Map<String, String> values;

    private TemplateVariables(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Builds the variables for a run.
     *
     * @param attached names of the uploaded files, for {@code ARTIFACTS}
     * @param changes  formatted change log, for {@code CHANGES}
     */
    @NonNull
    public static TemplateVariables of(@NonNull Run<?, ?> run, @NonNull EnvVars env, @NonNull Result result,
                                       @NonNull List<BuildFile> attached, @NonNull String changes) {
        Map<String, String> values = new HashMap<>(env);
        Job<?, ?> job = run.getParent();
        values.put("PROJECT_NAME", projectName(job));
        values.put("JOB_DISPLAY_NAME", job.getDisplayName());
        values.put("FULL_DISPLAY_NAME", run.getFullDisplayName());
        values.put("BUILD_DISPLAY_NAME", run.getDisplayName());
        values.put("BUILD_RESULT", result.toString());
        values.put("BUILD_DURATION", Util.getTimeSpanString(durationMillis(run)));
        values.put("ARTIFACTS", attached.stream().map(BuildFile::name).collect(Collectors.joining(", ")));
        values.put("CHANGES", changes);
        String commit = env.get("GIT_COMMIT");
        values.put("COMMIT_SHORT", TextUtils.isBlank(commit)
                ? ""
                : commit.substring(0, Math.min(SHORT_COMMIT_LENGTH, commit.length())));
        values.put("BRANCH", TextUtils.nullToEmpty(env.get("BRANCH_NAME")));
        if (TextUtils.isBlank(values.get("BUILD_URL"))) {
            String rootUrl = Jenkins.get().getRootUrl();
            if (rootUrl != null) {
                values.put("BUILD_URL", rootUrl + run.getUrl());
            }
        }
        if (TextUtils.isBlank(values.get("BUILD_NUMBER"))) {
            values.put("BUILD_NUMBER", String.valueOf(run.getNumber()));
        }
        return new TemplateVariables(values);
    }

    /**
     * For branch jobs of a Multibranch project (any job inside a computed folder) the project's display name,
     * otherwise the job's display name.
     */
    @NonNull
    public static String projectName(@NonNull Job<?, ?> job) {
        ItemGroup<?> parent = job.getParent();
        if (parent instanceof ComputedFolder<?> folder) {
            return folder.getDisplayName();
        }
        return job.getDisplayName();
    }

    /**
     * Build duration; for a build that is still running, the time since it started.
     */
    public static long durationMillis(@NonNull Run<?, ?> run) {
        long duration = run.getDuration();
        if (duration > 0) {
            return duration;
        }
        return Math.max(0, System.currentTimeMillis() - run.getStartTimeInMillis());
    }

    /**
     * Expands {@code ${NAME}} and {@code $NAME} references. Unknown variables are left as they are.
     */
    @NonNull
    public String expand(@CheckForNull String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        return TextUtils.nullToEmpty(Util.replaceMacro(template, values));
    }

    @CheckForNull
    public String get(@NonNull String name) {
        return values.get(name);
    }

    @NonNull
    public Map<String, String> asMap() {
        return Map.copyOf(values);
    }
}
