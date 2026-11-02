package gg.moonrise.jenkins.devbuild.discord;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.moonrise.jenkins.devbuild.FakeDiscord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordClientTest {

    private FakeDiscord discord;
    private ByteArrayOutputStream logBytes;
    private PrintStream log;

    @BeforeEach
    void setUp() throws Exception {
        discord = new FakeDiscord();
        logBytes = new ByteArrayOutputStream();
        log = new PrintStream(logBytes, true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void tearDown() {
        discord.close();
    }

    private DiscordClient client(int retries) {
        return new DiscordClient(Duration.ofSeconds(5), retries, Duration.ofMillis(10), log);
    }

    private String logText() {
        return logBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void sendsJsonWhenThereAreNoFiles() throws Exception {
        WebhookMessage message = new WebhookMessage().content("hello [ci] null").allowedMentions(List.of("roles"));
        try (DiscordClient client = client(0)) {
            DiscordResponse response = client.execute(discord.url(), null, message);
            assertEquals(FakeDiscord.CHANNEL_ID, response.channelId());
        }
        FakeDiscord.Request request = discord.only();
        assertEquals("POST", request.method());
        assertEquals("true", request.query().get("wait"));
        assertFalse(request.query().containsKey("thread_id"));
        assertTrue(request.contentType().startsWith("application/json"));
        JSONObject payload = request.payload();
        assertEquals("hello [ci] null", payload.getString("content"));
        assertEquals(List.of("roles"), payload.getJSONObject("allowed_mentions").getJSONArray("parse"));
    }

    @Test
    void uploadsFilesAsMultipartWithAttachmentsArray() throws Exception {
        byte[] first = "first-jar-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] second = new byte[70_000];
        for (int i = 0; i < second.length; i++) {
            second[i] = (byte) i;
        }
        WebhookMessage message = new WebhookMessage()
                .content("files")
                .attachment(new DiscordAttachment("a.jar", first.length, () -> new ByteArrayInputStream(first)))
                .attachment(new DiscordAttachment("b \"quoted\".bin", second.length, () -> new ByteArrayInputStream(second)));
        try (DiscordClient client = client(0)) {
            client.execute(discord.url(), "1234567890123456789", message);
        }
        FakeDiscord.Request request = discord.only();
        assertTrue(request.isMultipart());
        assertEquals("1234567890123456789", request.query().get("thread_id"));
        assertEquals("application/json; charset=UTF-8", request.part("payload_json").contentType());
        List<FakeDiscord.Part> files = request.files();
        assertEquals(2, files.size());
        assertEquals("files[0]", files.get(0).name());
        assertEquals("a.jar", files.get(0).filename());
        assertEquals("application/java-archive", files.get(0).contentType());
        assertArrayEquals(first, files.get(0).content());
        assertEquals("files[1]", files.get(1).name());
        assertEquals("b _quoted_.bin", files.get(1).filename());
        assertArrayEquals(second, files.get(1).content());
        JSONArray attachments = request.payload().getJSONArray("attachments");
        assertEquals(2, attachments.size());
        assertEquals(0, attachments.getJSONObject(0).getInt("id"));
        assertEquals("a.jar", attachments.getJSONObject(0).getString("filename"));
        assertEquals(1, attachments.getJSONObject(1).getInt("id"));
        assertEquals("b \"quoted\".bin", attachments.getJSONObject(1).getString("filename"));
    }

    @Test
    void retriesAfterRateLimitHonouringRetryAfter() throws Exception {
        discord.enqueue(429, "{\"message\":\"You are being rate limited.\",\"retry_after\":0.15,\"global\":false}",
                Map.of("Retry-After", "1"));
        byte[] content = "jar".getBytes(StandardCharsets.UTF_8);
        WebhookMessage message = new WebhookMessage()
                .attachment(new DiscordAttachment("a.jar", content.length, () -> new ByteArrayInputStream(content)));
        try (DiscordClient client = client(3)) {
            DiscordResponse response = client.execute(discord.url(), null, message);
            assertEquals(FakeDiscord.CHANNEL_ID, response.channelId());
        }
        List<FakeDiscord.Request> requests = discord.requests();
        assertEquals(2, requests.size());
        long waited = requests.get(1).receivedAtMillis() - requests.get(0).receivedAtMillis();
        assertTrue(waited >= 140, "waited only " + waited + " ms");
        assertTrue(waited < 900, "used the header instead of retry_after: " + waited + " ms");
        assertArrayEquals(content, requests.get(1).files().get(0).content(), "file re-sent on retry");
        assertTrue(logText().contains("Rate limited by Discord"));
    }

    @Test
    void givesUpAfterMaxRetries() throws Exception {
        for (int i = 0; i < 3; i++) {
            discord.enqueue(429, "{\"retry_after\":0.01}");
        }
        try (DiscordClient client = client(2)) {
            DiscordException e = assertThrows(DiscordException.class,
                    () -> client.execute(discord.url(), null, new WebhookMessage().content("x")));
            assertEquals(429, e.getStatusCode());
        }
        assertEquals(3, discord.requests().size());
    }

    @Test
    void doesNotRetryDeletedWebhookAndMasksToken() throws Exception {
        discord.enqueue(404, "{\"message\":\"Unknown Webhook\",\"code\":10015}");
        try (DiscordClient client = client(3)) {
            DiscordException e = assertThrows(DiscordException.class,
                    () -> client.execute(discord.url(), null, new WebhookMessage().content("x")));
            assertEquals(404, e.getStatusCode());
            assertTrue(e.isPermanent());
            assertTrue(e.getMessage().contains("deleted"), e.getMessage());
            assertFalse(e.getMessage().contains(FakeDiscord.TOKEN), e.getMessage());
            assertTrue(e.getMessage().contains("/api/webhooks/1234…/****"), e.getMessage());
        }
        assertEquals(1, discord.requests().size());
    }

    @Test
    void retriesServerErrors() throws Exception {
        discord.enqueue(502, "bad gateway");
        try (DiscordClient client = client(1)) {
            client.execute(discord.url(), null, new WebhookMessage().content("x"));
        }
        assertEquals(2, discord.requests().size());
    }

    @Test
    void connectionFailureDoesNotLeakUrl() {
        String url = "http://127.0.0.1:1/api/webhooks/123456789/secret-token";
        try (DiscordClient client = client(0)) {
            Exception e = assertThrows(Exception.class, () -> client.execute(url, null, new WebhookMessage().content("x")));
            assertFalse(e.getMessage().contains("secret-token"), e.getMessage());
        }
    }

    @Test
    void buildsUriWithExistingQuery() {
        assertEquals("https://discord.com/api/webhooks/1/t?wait=true&thread_id=42",
                DiscordClient.buildUri("https://discord.com/api/webhooks/1/t", "42").toString());
        assertEquals("https://discord.com/api/webhooks/1/t?x=1&wait=true",
                DiscordClient.buildUri("https://discord.com/api/webhooks/1/t?x=1", " ").toString());
    }
}
