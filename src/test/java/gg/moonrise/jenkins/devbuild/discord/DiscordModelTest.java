package gg.moonrise.jenkins.devbuild.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

class DiscordModelTest {

    @Test
    void jsonWriterKeepsStringsVerbatim() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", "null");
        map.put("b", "[ci] {x}");
        map.put("c", "line\n\"quoted\"\t\u0001 ");
        map.put("d", List.of(1, true, 2.5));
        String json = Json.write(map);
        assertEquals("{\"a\":\"null\",\"b\":\"[ci] {x}\",\"c\":\"line\\n\\\"quoted\\\"\\t\\u0001\\u2028\","
                + "\"d\":[1,true,2.5]}", json);
        JSONObject parsed = JSONObject.fromObject(json);
        assertEquals("line\n\"quoted\"\t\u0001 ", parsed.getString("c"));
    }

    @Test
    void embedIsTruncatedToDiscordLimits() {
        DiscordEmbed embed = new DiscordEmbed()
                .title("t".repeat(300))
                .description("d".repeat(5000))
                .footer("f".repeat(2100), null);
        for (int i = 0; i < 30; i++) {
            embed.field("n".repeat(300), "v".repeat(1100), false);
        }
        embed.applyLimits();
        assertTrue(embed.getTitle().length() <= DiscordLimits.EMBED_TITLE);
        assertTrue(embed.getTitle().endsWith("…"));
        assertTrue(embed.getFields().size() <= DiscordLimits.EMBED_FIELDS);
        for (DiscordEmbed.Field field : embed.getFields()) {
            assertTrue(field.name().length() <= DiscordLimits.FIELD_NAME);
            assertTrue(field.value().length() <= DiscordLimits.FIELD_VALUE);
        }
        assertTrue(embed.totalLength() <= DiscordLimits.EMBED_TOTAL, "total " + embed.totalLength());
        assertTrue(embed.getFooterText() == null || embed.getFooterText().length() <= DiscordLimits.FOOTER_TEXT);
    }

    @Test
    void smallEmbedIsUntouchedAndInvalidUrlsDropped() {
        DiscordEmbed embed = new DiscordEmbed().title("Title").url("not a url").description("desc")
                .field("", "", true).thumbnail("https://example.com/a.png").applyLimits();
        Map<String, Object> json = embed.toJson();
        assertEquals("Title", json.get("title"));
        assertFalse(json.containsKey("url"));
        assertEquals(Map.of("url", "https://example.com/a.png"), json.get("thumbnail"));
        assertEquals("​", embed.getFields().get(0).name());
    }

    @Test
    void messagePayloadContainsForumFieldsAndFlags() {
        WebhookMessage message = new WebhookMessage()
                .content("c".repeat(2100))
                .username("Builder")
                .threadName("Build 1")
                .appliedTags(List.of("1", "2", "3", "4", "5", "6"))
                .flags(DiscordLimits.FLAG_SUPPRESS_NOTIFICATIONS)
                .allowedMentions(List.of());
        Map<String, Object> payload = message.toPayload();
        assertEquals(DiscordLimits.CONTENT, ((String) payload.get("content")).length());
        assertEquals("Build 1", payload.get("thread_name"));
        assertEquals(List.of("1", "2", "3", "4", "5"), payload.get("applied_tags"));
        assertEquals(4096, payload.get("flags"));
        assertEquals(Map.of("parse", List.of()), payload.get("allowed_mentions"));
        assertNull(payload.get("attachments"));
    }

    @Test
    void webhookUrlsAreValidatedAndMasked() {
        assertTrue(WebhookUrls.isDiscordWebhookUrl("https://discord.com/api/webhooks/123456789012345678/abc-DEF_1"));
        assertTrue(WebhookUrls.isDiscordWebhookUrl("https://canary.discordapp.com/api/v10/webhooks/1/abc"));
        assertFalse(WebhookUrls.isDiscordWebhookUrl("http://discord.com/api/webhooks/1/abc"));
        assertFalse(WebhookUrls.isDiscordWebhookUrl("https://example.com/api/webhooks/1/abc"));
        assertEquals("https://discord.com/api/webhooks/1234…/****",
                WebhookUrls.mask("https://discord.com/api/webhooks/123456789012345678/super-secret?wait=true"));
        assertEquals("https://example.com/****", WebhookUrls.mask("https://example.com/hook/secret"));
        assertEquals("failed https://discord.com/api/webhooks/1234…/**** (****)",
                WebhookUrls.redact("failed https://discord.com/api/webhooks/123456789/tok (tok)",
                        "https://discord.com/api/webhooks/123456789/tok"));
    }

    @Test
    void responseParsing() {
        DiscordResponse response = DiscordResponse.parse("{\"id\":\"1\",\"channel_id\":\"2\"}");
        assertEquals("1", response.messageId());
        assertEquals("2", response.channelId());
        assertNull(DiscordResponse.parse("not json").messageId());
        assertNull(DiscordResponse.parse("").channelId());
    }
}
