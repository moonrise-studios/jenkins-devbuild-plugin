package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.discord.DiscordLimits;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.AbortException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits files into messages of at most 10 attachments and {@code maxUploadSizeMb} bytes each, applying the oversize
 * policy to files that can never fit and the {@code maxFiles} limit.
 */
public final class UploadPlanner {

    private UploadPlanner() {
    }

    /**
     * Plans the upload.
     *
     * @throws AbortException when a file is too large and the oversize policy is FAIL
     */
    @NonNull
    public static UploadPlan plan(@NonNull List<BuildFile> files, @NonNull ArtifactSettings settings,
                                  @NonNull PrintStream log) throws AbortException {
        long budget = settings.maxUploadBytes();
        List<List<BuildFile>> batches = new ArrayList<>();
        List<BuildFile> linked = new ArrayList<>();
        List<BuildFile> current = new ArrayList<>();
        long currentBytes = 0;
        int uploaded = 0;

        for (BuildFile file : files) {
            if (file.size() > budget) {
                handleOversize(file, settings, linked, log);
                continue;
            }
            if (uploaded >= settings.getMaxFiles()) {
                log.printf("[DevBuild] %s not uploaded: maxFiles (%d) reached%n", file.relativePath(), settings.getMaxFiles());
                linked.add(file);
                continue;
            }
            if (current.size() >= DiscordLimits.ATTACHMENTS_PER_MESSAGE || currentBytes + file.size() > budget) {
                batches.add(current);
                current = new ArrayList<>();
                currentBytes = 0;
            }
            current.add(file);
            currentBytes += file.size();
            uploaded++;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return new UploadPlan(batches, linked);
    }

    private static void handleOversize(BuildFile file, ArtifactSettings settings, List<BuildFile> linked,
                                       PrintStream log) throws AbortException {
        String description = file.relativePath() + " (" + TextUtils.formatSize(file.size())
                + ") is larger than the upload budget of " + settings.getMaxUploadSizeMb() + " MiB per message";
        switch (settings.getOversizePolicy()) {
            case LINK -> {
                log.println("[DevBuild] " + description + "; linking it instead");
                linked.add(file);
            }
            case SKIP -> log.println("[DevBuild] " + description + "; skipping it");
            case FAIL -> throw new AbortException("[DevBuild] " + description);
            default -> throw new IllegalStateException("Unknown oversize policy " + settings.getOversizePolicy());
        }
    }
}
