package gg.moonrise.jenkins.devbuild.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;

class DiscordModelTest {

    private static DiscordAttachment attachment(String filename) {
        return new DiscordAttachment(filename, 1, () -> new ByteArrayInputStream(new byte[] {1}));
    }

    private static JSONObject json(Map<String, Object> payload) {
        return JSONObject.fromObject(Json.write(payload));
    }

    private static List<String> texts(List<Map<String, Object>> components) {
        List<String> texts = new ArrayList<>();
        JSONArray array = JSONArray.fromObject(Json.write(components));
        collectTexts(array, texts);
        return texts;
    }

    private static void collectTexts(JSONArray components, List<String> texts) {
        for (int i = 0; i < components.size(); i++) {
            JSONObject component = components.getJSONObject(i);
            if (component.getInt("type") == 10) {
                texts.add(component.getString("content"));
            }
            if (component.has("components")) {
                collectTexts(component.getJSONArray("components"), texts);
            }
        }
    }

    private static List<String> fileUrls(JSONArray containerChildren) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < containerChildren.size(); i++) {
            JSONObject component = containerChildren.getJSONObject(i);
            if (component.getInt("type") == 13) {
                urls.add(component.getJSONObject("file").getString("url"));
            }
        }
        return urls;
    }

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
    void componentsPayloadLaysTheEmbedAndFilesOutInOneCard() {
        String timestamp = "2026-11-02T10:15:30Z";
        DiscordEmbed embed = new DiscordEmbed()
                .title("app #1").url("https://ci.example.com/job/app/1/").color(0x2ECC71).timestamp(timestamp)
                .author("Moonrise", "https://moonrise.gg", null).description("Built fine")
                .footer("Jenkins", null).thumbnail("https://example.com/logo.png").image("https://example.com/banner.png")
                .field("Result", "Success", true).field("Duration", "12 s", true)
                .field("Changes", "- fix", false).field("Commit", "`abc`", true)
                .applyLimits();
        List<DiscordAttachment> files = ComponentCard.withUniqueSafeNames(
                List.of(attachment("app 1.0.jar"), attachment("app_1.0.jar"), attachment("lib.jar")));
        WebhookMessage message = new WebhookMessage()
                .content("not sent")
                .tts(true)
                .username("Builds")
                .threadName("Build 1")
                .flags(DiscordLimits.FLAG_SUPPRESS_NOTIFICATIONS)
                .allowedMentions(List.of("roles"))
                .components(ComponentCard.render("<@&1> new build", embed, files));
        files.forEach(message::attachment);

        assertTrue(message.hasComponents());
        assertFalse(message.isEmpty());
        JSONObject payload = json(message.toPayload());
        assertFalse(payload.has("content"));
        assertFalse(payload.has("embeds"));
        assertFalse(payload.has("tts"));
        assertEquals(32768 | 4096, payload.getInt("flags"));
        assertEquals("Builds", payload.getString("username"));
        assertEquals("Build 1", payload.getString("thread_name"));
        assertEquals(List.of("roles"), payload.getJSONObject("allowed_mentions").getJSONArray("parse"));

        JSONArray components = payload.getJSONArray("components");
        assertEquals(2, components.size());
        assertEquals(10, components.getJSONObject(0).getInt("type"));
        assertEquals("<@&1> new build", components.getJSONObject(0).getString("content"));
        JSONObject container = components.getJSONObject(1);
        assertEquals(17, container.getInt("type"));
        assertEquals(0x2ECC71, container.getInt("accent_color"));
        JSONArray children = container.getJSONArray("components");
        assertEquals(11, children.size());

        JSONObject section = children.getJSONObject(0);
        assertEquals(9, section.getInt("type"));
        assertEquals(List.of("-# [Moonrise](https://moonrise.gg)", "### [app #1](https://ci.example.com/job/app/1/)",
                "Built fine"), section.getJSONArray("components").stream()
                .map(c -> ((JSONObject) c).getString("content")).toList());
        JSONObject thumbnail = section.getJSONObject("accessory");
        assertEquals(11, thumbnail.getInt("type"));
        assertEquals("https://example.com/logo.png", thumbnail.getJSONObject("media").getString("url"));

        assertEquals("**Result:** Success\n**Duration:** 12 s", children.getJSONObject(1).getString("content"));
        assertEquals("**Changes**\n- fix", children.getJSONObject(2).getString("content"));
        assertEquals("**Commit:** `abc`", children.getJSONObject(3).getString("content"));
        JSONObject gallery = children.getJSONObject(4);
        assertEquals(12, gallery.getInt("type"));
        assertEquals("https://example.com/banner.png",
                gallery.getJSONArray("items").getJSONObject(0).getJSONObject("media").getString("url"));
        JSONObject separator = children.getJSONObject(5);
        assertEquals(14, separator.getInt("type"));
        assertTrue(separator.getBoolean("divider"));
        assertEquals(1, separator.getInt("spacing"));
        assertEquals("**Download here**", children.getJSONObject(6).getString("content"));
        assertEquals(List.of("attachment://app_1.0.jar", "attachment://app_1.0-2.jar", "attachment://lib.jar"),
                fileUrls(children));
        long epoch = Instant.parse(timestamp).getEpochSecond();
        assertEquals("-# Jenkins · <t:" + epoch + ":f>", children.getJSONObject(10).getString("content"));

        JSONArray attachments = payload.getJSONArray("attachments");
        List<String> uploaded = IntStream.range(0, attachments.size())
                .mapToObj(i -> "attachment://" + attachments.getJSONObject(i).getString("filename")).toList();
        assertEquals(fileUrls(children), uploaded, "every file component references an uploaded file");
    }

    @Test
    void leadingInlineFieldsSitBesideTheThumbnail() {
        DiscordEmbed embed = new DiscordEmbed().title("app #1").thumbnail("https://example.com/logo.png")
                .field("Result", "Success", true).field("Branch", "`main`", true)
                .field("Changes", "- fix", false).applyLimits();

        JSONArray children = JSONArray.fromObject(Json.write(ComponentCard.render(null, embed, List.of())))
                .getJSONObject(0).getJSONArray("components");

        assertEquals(2, children.size());
        JSONObject section = children.getJSONObject(0);
        assertEquals(9, section.getInt("type"));
        assertEquals(List.of("### app #1", "**Result:** Success\n**Branch:** `main`"),
                section.getJSONArray("components").stream().map(c -> ((JSONObject) c).getString("content")).toList());
        assertEquals("**Changes**\n- fix", children.getJSONObject(1).getString("content"));

        DiscordEmbed blockFirst = new DiscordEmbed().title("app #1").thumbnail("https://example.com/logo.png")
                .field("Changes", "- fix", false).field("Result", "Success", true).applyLimits();
        JSONArray blockChildren = JSONArray.fromObject(Json.write(ComponentCard.render(null, blockFirst, List.of())))
                .getJSONObject(0).getJSONArray("components");
        assertEquals(1, blockChildren.getJSONObject(0).getJSONArray("components").size(),
                "a leading non-inline field stays below the section");
    }

    @Test
    void cardWithoutHeaderTextSkipsTheThumbnailAndColor() {
        DiscordEmbed embed = new DiscordEmbed().thumbnail("https://example.com/logo.png").timestamp("yesterday")
                .field("", "unnamed", true).applyLimits();
        List<Map<String, Object>> card = ComponentCard.render(" ", embed, List.of());
        JSONArray components = JSONArray.fromObject(Json.write(card));
        assertEquals(1, components.size(), "blank message text is left out");
        JSONObject container = components.getJSONObject(0);
        assertFalse(container.has("accent_color"));
        assertEquals(1, container.getJSONArray("components").size());
        assertEquals("unnamed", container.getJSONArray("components").getJSONObject(0).getString("content"));

        DiscordEmbed titled = new DiscordEmbed().title("A [b] title").url("https://ci/job/a(b)/1/").applyLimits();
        assertEquals(List.of("### [A \\[b\\] title](https://ci/job/a%28b%29/1/)"),
                texts(ComponentCard.render(null, titled, List.of())));
        assertTrue(ComponentCard.render(null, new DiscordEmbed(), List.of()).isEmpty());
    }

    @Test
    void attachmentNamesAreMadeSafeAndUnique() {
        List<String> names = ComponentCard.withUniqueSafeNames(List.of(
                        attachment("app 1.0.jar"), attachment("app_1.0.jar"), attachment("app_1.0.jar"),
                        attachment("ünï😀.jar"), attachment(" "), attachment("README"), attachment("README"),
                        attachment(".hidden")))
                .stream().map(DiscordAttachment::filename).toList();
        assertEquals(List.of("app_1.0.jar", "app_1.0-2.jar", "app_1.0-3.jar", "_n__.jar", "file", "README",
                "README-2", ".hidden"), names);
    }

    @Test
    void cardIsCutToTheTextLimitInEmbedOrder() {
        DiscordEmbed embed = new DiscordEmbed()
                .title("t".repeat(300))
                .description("d".repeat(5000))
                .footer("f".repeat(2100), null)
                .timestamp("2026-11-02T10:15:30Z");
        for (int i = 0; i < 30; i++) {
            embed.field("n" + i, "v".repeat(1100), false);
        }
        embed.applyLimits();
        List<DiscordAttachment> files = IntStream.range(0, 10).mapToObj(i -> attachment("f" + i + ".jar")).toList();

        List<Map<String, Object>> card = ComponentCard.render("m".repeat(2500), embed, files);

        assertTrue(ComponentCard.textLength(card) <= DiscordLimits.COMPONENT_TEXT_TOTAL,
                "text " + ComponentCard.textLength(card));
        assertTrue(ComponentCard.componentCount(card) <= DiscordLimits.COMPONENTS_TOTAL);
        List<String> texts = texts(card);
        assertEquals("m".repeat(DiscordLimits.CONTENT - 1) + "…", texts.get(0), "message text is cut last");
        assertEquals("### " + "t".repeat(DiscordLimits.EMBED_TITLE - 1) + "…", texts.get(1));
        assertTrue(texts.stream().noneMatch(text -> text.startsWith("d") || text.startsWith("**n")),
                "description and fields go first: " + texts.stream().map(t -> t.substring(0, 5)).toList());
        assertTrue(texts.get(texts.size() - 1).startsWith("-# fff"), "footer is shortened, not dropped");
        JSONArray children = JSONArray.fromObject(Json.write(card)).getJSONObject(1).getJSONArray("components");
        assertEquals(10, fileUrls(children).size(), "files are never dropped");
    }

    @Test
    void cardDropsTrailingFieldsToFitTheComponentLimit() {
        DiscordEmbed embed = new DiscordEmbed().title("t").thumbnail("https://example.com/logo.png").footer("f", null);
        for (int i = 0; i < 25; i++) {
            embed.field("n" + i, "v", false);
        }
        embed.applyLimits();
        List<DiscordAttachment> files = IntStream.range(0, 10).mapToObj(i -> attachment("f" + i + ".jar")).toList();

        List<Map<String, Object>> card = ComponentCard.render(null, embed, files);

        assertEquals(DiscordLimits.COMPONENTS_TOTAL, ComponentCard.componentCount(card));
        List<String> texts = texts(card);
        assertTrue(texts.contains("**n22**\nv"), texts.toString());
        assertFalse(texts.contains("**n23**\nv"), texts.toString());
        assertEquals("-# f", texts.get(texts.size() - 1));
    }

    @Test
    void componentsAreCountedAsContent() {
        WebhookMessage message = new WebhookMessage();
        assertTrue(message.isEmpty());
        message.components(ComponentCard.render("hi", new DiscordEmbed(), List.of()));
        assertFalse(message.isEmpty());
        assertEquals(DiscordLimits.FLAG_IS_COMPONENTS_V2, message.toPayload().get("flags"));
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
