package gg.moonrise.jenkins.devbuild;

import edu.umd.cs.findbugs.annotations.NonNull;
import gg.moonrise.jenkins.devbuild.publish.DevBuildNotifier;
import gg.moonrise.jenkins.devbuild.publish.EffectiveSettings;
import gg.moonrise.jenkins.devbuild.publish.SettingsResolver;
import gg.moonrise.jenkins.devbuild.publish.SettingsResolver.AutoPublishLevel;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishes completed builds of jobs whose nearest DevBuild job or folder property has {@code autoPublish} enabled.
 * Uses archived artifacts only. Never fails the build: every error is logged.
 */
@Extension
public class DevBuildRunListener extends RunListener<Run<?, ?>> {

    private static final Logger LOGGER = Logger.getLogger(DevBuildRunListener.class.getName());

    @Override
    public void onCompleted(Run<?, ?> run, @NonNull TaskListener listener) {
        try {
            autoPublish(run, listener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.getLogger().println("[DevBuild] Interrupted while posting to Discord");
        } catch (IOException e) {
            listener.getLogger().println("[DevBuild] Discord notification failed: " + e.getMessage());
        } catch (RuntimeException e) {
            listener.getLogger().println("[DevBuild] Discord notification failed unexpectedly: "
                    + e.getClass().getSimpleName());
            LOGGER.log(Level.WARNING, "Unexpected {0} while posting {1} to Discord",
                    new Object[] {e.getClass().getName(), run.getFullDisplayName()});
        }
    }

    private static void autoPublish(Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
        Job<?, ?> job = run.getParent();
        if (job.getParent() instanceof Job) {
            // Matrix configurations and Maven modules: the parent build is published instead.
            return;
        }
        Optional<AutoPublishLevel> level = SettingsResolver.nearestPropertyLevel(job);
        if (level.isEmpty() || !level.get().config().isAutoPublish()) {
            return;
        }
        if (level.get().config().isSkipIfStepUsed() && run.getAction(DevBuildAction.class) != null) {
            listener.getLogger().println("[DevBuild] Discord step already ran for this build; not posting again");
            return;
        }
        EffectiveSettings settings = SettingsResolver.resolve(job, null);
        EnvVars env = run.getEnvironment(listener);
        Result current = run.getResult();
        Result result = current == null ? Result.SUCCESS : current;
        DevBuildNotifier.publish(run, null, env, listener, settings, result);
    }
}
