package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.ArtifactSource;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Run;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.util.VirtualFile;

/**
 * Finds the files matching the artifact patterns, in the archived artifacts or the workspace.
 */
public final class ArtifactCollector {

    private ArtifactCollector() {
    }

    /**
     * Lists matching files sorted by path. Archived artifacts are read through the run's artifact manager and
     * workspace files through {@link FilePath}, so both work when the build ran on an agent.
     *
     * @param workspace the workspace, or {@code null} when the step runs outside {@code node} or automatically
     */
    @NonNull
    public static List<BuildFile> collect(@NonNull Run<?, ?> run, @CheckForNull FilePath workspace,
                                          @NonNull ArtifactSettings settings, @NonNull PrintStream log)
            throws IOException, InterruptedException {
        String includes = TextUtils.normalizePatterns(settings.getIncludes());
        if (includes.isEmpty()) {
            return List.of();
        }
        String excludes = TextUtils.normalizePatterns(settings.getExcludes());
        ArtifactSource source = settings.getSource();
        List<BuildFile> files = new ArrayList<>();
        if (source != ArtifactSource.WORKSPACE) {
            files.addAll(archived(run, includes, excludes));
        }
        boolean useWorkspace = source == ArtifactSource.WORKSPACE || (source == ArtifactSource.AUTO && files.isEmpty());
        if (useWorkspace) {
            if (workspace == null) {
                if (source == ArtifactSource.WORKSPACE) {
                    log.println("[DevBuild] Artifact source is WORKSPACE but no workspace is available; "
                            + "run the step inside a node block. No files attached.");
                }
            } else {
                files.addAll(workspace(workspace, includes, excludes));
            }
        }
        files.sort(Comparator.comparing(BuildFile::relativePath));
        return files;
    }

    private static List<BuildFile> archived(Run<?, ?> run, String includes, String excludes)
            throws IOException {
        VirtualFile root = run.getArtifactManager().root();
        if (!root.exists()) {
            return List.of();
        }
        Collection<String> paths = root.list(includes, excludes.isEmpty() ? null : excludes, false);
        String baseUrl = artifactBaseUrl(run);
        List<BuildFile> files = new ArrayList<>();
        for (String path : paths) {
            String normalized = path.replace('\\', '/');
            VirtualFile file = root.child(normalized);
            if (!file.isFile()) {
                continue;
            }
            String url = baseUrl == null ? null : baseUrl + encodePath(normalized);
            files.add(new BuildFile(file.getName(), normalized, file.length(), url, file::open));
        }
        return files;
    }

    private static List<BuildFile> workspace(FilePath workspace, String includes, String excludes)
            throws IOException, InterruptedException {
        if (!workspace.exists()) {
            return List.of();
        }
        FilePath[] matches = workspace.list(includes, excludes.isEmpty() ? null : excludes, false);
        String root = workspace.getRemote();
        List<BuildFile> files = new ArrayList<>();
        for (FilePath file : Arrays.asList(matches)) {
            String remote = file.getRemote();
            String relative = remote.startsWith(root) && remote.length() > root.length()
                    ? remote.substring(root.length() + 1)
                    : file.getName();
            files.add(new BuildFile(file.getName(), relative.replace('\\', '/'), file.length(), null, file::read));
        }
        return files;
    }

    @CheckForNull
    private static String artifactBaseUrl(Run<?, ?> run) {
        String rootUrl = Jenkins.get().getRootUrl();
        if (rootUrl == null) {
            return null;
        }
        return rootUrl + run.getUrl() + "artifact/";
    }

    private static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("/")) {
            if (out.length() > 0) {
                out.append('/');
            }
            out.append(Util.rawEncode(segment));
        }
        return out.toString();
    }
}
