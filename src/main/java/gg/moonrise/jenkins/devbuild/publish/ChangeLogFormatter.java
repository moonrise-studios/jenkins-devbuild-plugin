package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.RunWithSCM;

/**
 * Formats the SCM changes of a build as Markdown lines.
 */
public final class ChangeLogFormatter {

    private static final Logger LOGGER = Logger.getLogger(ChangeLogFormatter.class.getName());
    private static final int MAX_MESSAGE_LENGTH = 100;
    private static final int SHORT_COMMIT_LENGTH = 7;

    private ChangeLogFormatter() {
    }

    /**
     * Returns up to {@code max} lines such as {@code - `abc1234` Fix the thing (alice)}, followed by
     * {@code … and N more} when there are more changes. Empty when the build has no changes.
     */
    @NonNull
    public static String format(@NonNull Run<?, ?> run, int max) {
        List<ChangeLogSet.Entry> entries = entries(run);
        if (entries.isEmpty() || max <= 0) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(max, entries.size()); i++) {
            lines.add(formatEntry(entries.get(i)));
        }
        if (entries.size() > max) {
            lines.add(TextUtils.ELLIPSIS + " and " + (entries.size() - max) + " more");
        }
        return String.join("\n", lines);
    }

    private static List<ChangeLogSet.Entry> entries(Run<?, ?> run) {
        List<ChangeLogSet.Entry> entries = new ArrayList<>();
        if (!(run instanceof RunWithSCM<?, ?> withScm)) {
            return entries;
        }
        try {
            for (ChangeLogSet<? extends ChangeLogSet.Entry> set : withScm.getChangeSets()) {
                for (ChangeLogSet.Entry entry : set) {
                    entries.add(entry);
                }
            }
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "Could not read the change log of " + run, e);
        }
        return entries;
    }

    private static String formatEntry(ChangeLogSet.Entry entry) {
        StringBuilder line = new StringBuilder("- ");
        String commitId = entry.getCommitId();
        if (commitId != null && !commitId.isBlank()) {
            String shortId = commitId.length() > SHORT_COMMIT_LENGTH ? commitId.substring(0, SHORT_COMMIT_LENGTH) : commitId;
            line.append('`').append(shortId.replace("`", "")).append("` ");
        }
        String message = TextUtils.nullToEmpty(entry.getMsg()).strip();
        int newline = message.indexOf('\n');
        if (newline >= 0) {
            message = message.substring(0, newline).strip();
        }
        line.append(TextUtils.truncate(message, MAX_MESSAGE_LENGTH));
        String author = authorName(entry);
        if (!author.isEmpty()) {
            line.append(" (").append(author).append(')');
        }
        return line.toString();
    }

    private static String authorName(ChangeLogSet.Entry entry) {
        try {
            User author = entry.getAuthor();
            return author == null ? "" : author.getFullName();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
