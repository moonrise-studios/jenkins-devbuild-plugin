package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Formats file lists for the embed's Artifacts field and for plain message content.
 */
public final class ArtifactListFormatter {

    private ArtifactListFormatter() {
    }

    /**
     * One line per file, e.g. {@code [app.jar](https://jenkins/…/app.jar) · 1.2 MiB}. Files in {@code linked} are
     * marked as not uploaded. Lines that do not fit in {@code maxLength} are summarised as {@code … and N more}.
     */
    @NonNull
    public static String fieldValue(@NonNull List<BuildFile> files, @NonNull Set<BuildFile> linked, int maxLength) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < files.size(); i++) {
            BuildFile file = files.get(i);
            String line = line(file, linked.contains(file));
            int remainingAfter = files.size() - i - 1;
            String reserve = remainingAfter > 0 ? "\n" + more(remainingAfter) : "";
            int separator = value.length() == 0 ? 0 : 1;
            if (value.length() + separator + line.length() + reserve.length() > maxLength) {
                if (value.length() == 0) {
                    return TextUtils.truncate(line, maxLength);
                }
                return value.append('\n').append(more(files.size() - i)).toString();
            }
            if (separator > 0) {
                value.append('\n');
            }
            value.append(line);
        }
        return value.toString();
    }

    private static String more(int count) {
        return TextUtils.ELLIPSIS + " and " + count + " more";
    }

    /**
     * Lines for files that were not uploaded, used when the embed is disabled.
     */
    @NonNull
    public static List<String> linkedLines(@NonNull List<BuildFile> linked) {
        List<String> lines = new ArrayList<>();
        for (BuildFile file : linked) {
            lines.add(line(file, true));
        }
        return lines;
    }

    private static String line(BuildFile file, boolean notUploaded) {
        String label = TextUtils.escapeMarkdownLabel(file.name());
        String name = file.url() == null ? label : "[" + label + "](" + file.url() + ")";
        return name + " · " + TextUtils.formatSize(file.size()) + (notUploaded ? " (not attached)" : "");
    }
}
