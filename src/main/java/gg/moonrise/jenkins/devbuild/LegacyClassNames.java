package gg.moonrise.jenkins.devbuild;

import gg.moonrise.jenkins.devbuild.build.DevBuildAction;
import gg.moonrise.jenkins.devbuild.build.DevBuildPublisher;
import gg.moonrise.jenkins.devbuild.config.DevBuildFolderProperty;
import gg.moonrise.jenkins.devbuild.config.DevBuildGlobalConfiguration;
import gg.moonrise.jenkins.devbuild.config.DevBuildJobProperty;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.settings.ArtifactSource;
import gg.moonrise.jenkins.devbuild.settings.EmbedField;
import gg.moonrise.jenkins.devbuild.settings.EmbedSettings;
import gg.moonrise.jenkins.devbuild.settings.FilePlacement;
import gg.moonrise.jenkins.devbuild.settings.MessageSettings;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import gg.moonrise.jenkins.devbuild.settings.OversizePolicy;
import gg.moonrise.jenkins.devbuild.webhook.DiscordWebhook;
import gg.moonrise.jenkins.devbuild.webhook.NamedWebhook;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Items;
import hudson.model.Run;
import hudson.util.XStream2;
import java.util.List;
import jenkins.model.Jenkins;

/**
 * Lets Jenkins read configuration saved by releases up to 1.2.1, when these classes lived in the root package.
 *
 * <p>Jenkins stores describables by fully qualified class name in the global configuration file, job and folder
 * {@code config.xml} and {@code build.xml}. Each old name is mapped to the moved class; new saves use the new name.
 */
public final class LegacyClassNames {

    private static final String OLD_PACKAGE = "gg.moonrise.jenkins.devbuild.";

    /**
     * Every moved class that can appear in saved XML, either as an element name or in a {@code class} attribute.
     */
    private static final List<Class<?>> MOVED_CLASSES = List.of(
            DevBuildAction.class,
            DevBuildPublisher.class,
            DevBuildFolderProperty.class,
            DevBuildGlobalConfiguration.class,
            DevBuildJobProperty.class,
            ArtifactSettings.class,
            ArtifactSource.class,
            EmbedField.class,
            EmbedSettings.class,
            FilePlacement.class,
            MessageSettings.class,
            NotifyConditions.class,
            OversizePolicy.class,
            DiscordWebhook.class,
            NamedWebhook.class);

    private LegacyClassNames() {
    }

    @Initializer(before = InitMilestone.PLUGINS_STARTED)
    public static void addAliases() {
        for (XStream2 xstream : List.of(Jenkins.XSTREAM2, Items.XSTREAM2, Run.XSTREAM2)) {
            for (Class<?> type : MOVED_CLASSES) {
                xstream.addCompatibilityAlias(oldName(type), type);
            }
        }
    }

    /**
     * The name the class had before it moved to a sub-package.
     */
    public static String oldName(Class<?> type) {
        return OLD_PACKAGE + type.getSimpleName();
    }
}
