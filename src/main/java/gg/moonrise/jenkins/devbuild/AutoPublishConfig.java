package gg.moonrise.jenkins.devbuild;

/**
 * A configuration level that can publish automatically when a build completes (job and folder properties).
 */
public interface AutoPublishConfig extends DevBuildConfig {

    /**
     * Whether to post automatically when a build of a job at or below this level completes.
     */
    boolean isAutoPublish();

    /**
     * Whether the automatic post is skipped when the step or post-build action already ran for the build.
     */
    boolean isSkipIfStepUsed();
}
