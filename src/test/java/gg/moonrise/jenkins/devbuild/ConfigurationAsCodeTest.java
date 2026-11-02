package gg.moonrise.jenkins.devbuild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.moonrise.jenkins.devbuild.config.DevBuildGlobalConfiguration;
import gg.moonrise.jenkins.devbuild.settings.EmbedSettings;
import gg.moonrise.jenkins.devbuild.settings.FilePlacement;
import gg.moonrise.jenkins.devbuild.settings.OversizePolicy;
import gg.moonrise.jenkins.devbuild.webhook.NamedWebhook;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.Util;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import io.jenkins.plugins.casc.model.CNode;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("casc.yml")
    void configuresGlobalSettings(JenkinsConfiguredWithCodeRule r) {
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        assertEquals("devbuilds", global.getDefaultWebhookName());
        assertEquals(20, global.getTimeoutSeconds());
        assertEquals(5, global.getMaxRetries());
        assertEquals(2, global.getWebhooks().size());
        NamedWebhook releases = global.findWebhook("releases").orElseThrow();
        assertEquals("discord-devbuilds", releases.getWebhook().getCredentialsId());
        assertEquals("Release ${BUILD_NUMBER}", releases.getWebhook().getThreadName());
        assertEquals("111111111111111111", releases.getWebhook().getAppliedTags());
        assertEquals("Jenkins", global.getMessage().getUsername());
        assertFalse(global.getMessage().isMentionRoles());
        assertTrue(global.getMessage().isMentionUsers());
        assertEquals("Built by Jenkins", global.getEmbed().getFooterText());
        assertEquals("#00FF00", global.getEmbed().getSuccessColor());
        assertEquals("${PROJECT_NAME} #${BUILD_NUMBER}", global.getEmbed().getTitle());
        assertEquals(EmbedSettings.DEFAULT_THUMBNAIL_URL, global.getEmbed().getThumbnailUrl());
        assertEquals("${VERSION}", global.getEmbed().getFields().get(0).getValue());
        assertTrue(global.getEmbed().getFields().get(0).isInline());
        assertEquals("build/libs/*.jar", global.getArtifacts().getIncludes());
        assertEquals(OversizePolicy.SKIP, global.getArtifacts().getOversizePolicy());
        assertEquals(8, global.getArtifacts().getMaxUploadSizeMb());
        assertEquals(FilePlacement.WITH_EMBED, global.getArtifacts().getPlacement());
        assertTrue(global.getConditions().isNotifyOnAborted());
        assertEquals("main|release/.*", global.getConditions().getBranchPattern());
    }

    @Test
    @ConfiguredWithCode("casc.yml")
    void exportsGlobalSettings(JenkinsConfiguredWithCodeRule r) throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode node = Util.getUnclassifiedRoot(context).get("discordDevBuild");
        String exported = Util.toYamlString(node);
        assertTrue(exported.contains("defaultWebhookName: \"devbuilds\""), exported);
        assertTrue(exported.contains("name: \"releases\""), exported);
        assertTrue(exported.contains("oversizePolicy: SKIP"), exported);
        assertTrue(exported.contains("placement: WITH_EMBED"), exported);
        assertNull(DevBuildGlobalConfiguration.get().getWebhooks().get(0).getWebhook().getWebhookUrl());
    }
}
