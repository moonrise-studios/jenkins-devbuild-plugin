package gg.moonrise.jenkins.devbuild.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * String helpers shared by the message composer and the configuration classes.
 */
public final class TextUtils {

    /** Marker appended to text cut down to fit a Discord limit. */
    public static final String ELLIPSIS = "…";

    private static final long KIB = 1024L;

    private TextUtils() {
    }

    /**
     * Returns {@code true} when the value is {@code null} or only whitespace.
     */
    public static boolean isBlank(@CheckForNull String value) {
        return value == null || value.isBlank();
    }

    /**
     * Trims the value and turns blank values into {@code null}, the representation used for unset form fields.
     */
    @CheckForNull
    public static String trimToNull(@CheckForNull String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Returns the value or an empty string when it is {@code null}.
     */
    @NonNull
    public static String nullToEmpty(@CheckForNull String value) {
        return value == null ? "" : value;
    }

    /**
     * Cuts the text to at most {@code max} UTF-16 code units, ending with an ellipsis when it was shortened. Never
     * splits a surrogate pair.
     */
    @NonNull
    public static String truncate(@CheckForNull String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max <= 0) {
            return "";
        }
        if (max == 1) {
            return ELLIPSIS;
        }
        int end = max - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    /**
     * Splits a comma separated list, trimming entries and dropping empty ones.
     */
    @NonNull
    public static List<String> splitCommaList(@CheckForNull String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Normalises an Ant pattern list such as {@code "a/*.jar, b/*.jar"} to {@code "a/*.jar,b/*.jar"}.
     */
    @NonNull
    public static String normalizePatterns(@CheckForNull String patterns) {
        return String.join(",", splitCommaList(patterns));
    }

    /**
     * Formats a byte count as a human readable size, e.g. {@code 1.4 MiB}.
     */
    @NonNull
    public static String formatSize(long bytes) {
        if (bytes < KIB) {
            return bytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = -1;
        while (value >= KIB && unit < units.length - 1) {
            value /= KIB;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    /**
     * Escapes characters that would break a Markdown link label.
     */
    @NonNull
    public static String escapeMarkdownLabel(@NonNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '[' || c == ']' || c == '*' || c == '_' || c == '`' || c == '~' || c == '\\' || c == '|') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
