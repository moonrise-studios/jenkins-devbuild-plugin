package gg.moonrise.jenkins.devbuild.config;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import gg.moonrise.jenkins.devbuild.discord.WebhookUrls;
import gg.moonrise.jenkins.devbuild.util.ColorUtils;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import gg.moonrise.jenkins.devbuild.webhook.NamedWebhook;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Form validation and list helpers shared by several descriptors.
 */
public final class DevBuildForms {

    private DevBuildForms() {
    }

    /**
     * Accepts blank values, templates containing {@code ${...}} and absolute http(s) URLs.
     */
    public static FormValidation checkTemplateUrl(@CheckForNull String value) {
        if (TextUtils.isBlank(value) || value.contains("${")) {
            return FormValidation.ok();
        }
        return WebhookUrls.isHttpUrl(value) ? FormValidation.ok() : FormValidation.error("Not an http(s) URL");
    }

    public static FormValidation checkHexColor(@CheckForNull String value) {
        return ColorUtils.isValidHex(value)
                ? FormValidation.ok()
                : FormValidation.error("Use a hex colour such as #2ECC71");
    }

    public static FormValidation checkRegex(@CheckForNull String value) {
        if (TextUtils.isBlank(value)) {
            return FormValidation.ok();
        }
        try {
            Pattern.compile(value);
            return FormValidation.ok();
        } catch (PatternSyntaxException e) {
            return FormValidation.error("Invalid regular expression: " + e.getDescription());
        }
    }

    public static FormValidation checkPositive(int value, int max, String what) {
        if (value < 1) {
            return FormValidation.error(what + " must be at least 1");
        }
        if (value > max) {
            return FormValidation.warning(what + " above " + max + " is not useful");
        }
        return FormValidation.ok();
    }

    /**
     * Webhook names from the global configuration, with an empty entry meaning "inherit".
     */
    public static ListBoxModel webhookNameItems(@CheckForNull String current) {
        ListBoxModel items = new ListBoxModel();
        items.add("(inherit)", "");
        DevBuildGlobalConfiguration global = DevBuildGlobalConfiguration.get();
        boolean found = TextUtils.isBlank(current);
        for (NamedWebhook webhook : global.getWebhooks()) {
            items.add(webhook.getName());
            if (webhook.getName().equals(current)) {
                found = true;
            }
        }
        if (!found) {
            items.add(current + " (not defined)", current);
        }
        return items;
    }
}
