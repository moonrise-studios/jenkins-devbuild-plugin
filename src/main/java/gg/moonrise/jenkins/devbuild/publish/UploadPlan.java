package gg.moonrise.jenkins.devbuild.publish;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * How the selected files are delivered.
 *
 * @param batches files uploaded per message, in order; {@link MessageComposer} decides which message carries each
 * @param linked  files listed in the embed (with a Jenkins link when archived) but not uploaded
 */
public record UploadPlan(@NonNull List<List<BuildFile>> batches, @NonNull List<BuildFile> linked) {

    public UploadPlan {
        batches = batches.stream().map(List::copyOf).toList();
        linked = List.copyOf(linked);
    }

    /**
     * A plan that uploads nothing and lists every file.
     */
    @NonNull
    public static UploadPlan listOnly(@NonNull List<BuildFile> files) {
        return new UploadPlan(List.of(), files);
    }

    /**
     * Every uploaded file, in upload order.
     */
    @NonNull
    public List<BuildFile> attached() {
        List<BuildFile> all = new ArrayList<>();
        batches.forEach(all::addAll);
        return all;
    }
}
