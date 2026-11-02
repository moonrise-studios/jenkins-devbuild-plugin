package gg.moonrise.jenkins.devbuild.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Parses the hex colour strings used in the embed settings.
 */
public final class ColorUtils {

    private static final Pattern HEX_COLOR = Pattern.compile("^#?[0-9A-Fa-f]{6}$");

    private ColorUtils() {
    }

    /**
     * Returns {@code true} for {@code #RRGGBB} or {@code RRGGBB}.
     */
    public static boolean isValidHex(@CheckForNull String value) {
        return value != null && HEX_COLOR.matcher(value.trim()).matches();
    }

    /**
     * Parses {@code #RRGGBB} into the integer colour Discord expects.
     */
    public static OptionalInt parseHex(@CheckForNull String value) {
        if (!isValidHex(value)) {
            return OptionalInt.empty();
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        return OptionalInt.of(Integer.parseInt(hex, 16));
    }
}
