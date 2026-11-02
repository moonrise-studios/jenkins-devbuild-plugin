package gg.moonrise.jenkins.devbuild;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * A local stand-in for the Discord webhook endpoint. Records every request, parses multipart bodies and answers like
 * Discord with {@code wait=true}. Responses can be queued to simulate errors and rate limits.
 */
public final class FakeDiscord implements AutoCloseable {

    public static final String WEBHOOK_ID = "123456789012345678";
    public static final String TOKEN = "fake-Token_value.123";
    public static final String CHANNEL_ID = "555000000000000000";
    public static final String NEW_THREAD_ID = "777000000000000001";

    public static final int COMPONENTS_V2_FLAG = 1 << 15;

    private static final int SECTION = 9;
    private static final int TEXT_DISPLAY = 10;
    private static final int FILE = 13;
    private static final int CONTAINER = 17;
    private static final Pattern BOUNDARY = Pattern.compile("boundary=([^;]+)");
    private static final Pattern NAME = Pattern.compile("name=\"([^\"]*)\"");
    private static final Pattern FILENAME = Pattern.compile("filename=\"([^\"]*)\"");

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Deque<Response> queued = new ArrayDeque<>();
    private final AtomicLong messageIds = new AtomicLong(900000000000000000L);

    /** A captured multipart part. */
    public record Part(String name, String filename, String contentType, byte[] content) {
        public String text() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    /** A captured request. */
    public record Request(String method, String path, Map<String, String> query, String contentType, byte[] body,
                          List<Part> parts, long receivedAtMillis) {

        public boolean isMultipart() {
            return contentType != null && contentType.startsWith("multipart/form-data");
        }

        public JSONObject payload() {
            if (isMultipart()) {
                return JSONObject.fromObject(part("payload_json").text());
            }
            return JSONObject.fromObject(new String(body, StandardCharsets.UTF_8));
        }

        public String rawPayload() {
            return isMultipart() ? part("payload_json").text() : new String(body, StandardCharsets.UTF_8);
        }

        public Part part(String name) {
            return parts.stream().filter(p -> p.name().equals(name)).findFirst()
                    .orElseThrow(() -> new AssertionError("No part " + name + " in " + parts.stream().map(Part::name).toList()));
        }

        public List<Part> files() {
            return parts.stream().filter(p -> p.name().startsWith("files[")).toList();
        }

        public JSONObject embed() {
            JSONArray embeds = payload().getJSONArray("embeds");
            return embeds.getJSONObject(0);
        }

        public JSONObject field(String name) {
            JSONArray fields = embed().optJSONArray("fields");
            if (fields != null) {
                for (int i = 0; i < fields.size(); i++) {
                    if (fields.getJSONObject(i).getString("name").equals(name)) {
                        return fields.getJSONObject(i);
                    }
                }
            }
            return null;
        }

        /** Whether the message is a Components V2 message. */
        public boolean isComponentsV2() {
            return (payload().optInt("flags") & COMPONENTS_V2_FLAG) != 0;
        }

        /** The top-level Text Display above the card (the message text), or {@code null}. */
        public String messageText() {
            JSONArray components = payload().getJSONArray("components");
            JSONObject first = components.getJSONObject(0);
            return first.getInt("type") == TEXT_DISPLAY ? first.getString("content") : null;
        }

        /** The Container of a Components V2 message. */
        public JSONObject card() {
            JSONArray components = payload().getJSONArray("components");
            for (int i = 0; i < components.size(); i++) {
                if (components.getJSONObject(i).getInt("type") == CONTAINER) {
                    return components.getJSONObject(i);
                }
            }
            throw new AssertionError("No container in " + components);
        }

        /** Every Text Display in the card, in order, including those in a Section. */
        public List<String> cardTexts() {
            List<String> texts = new ArrayList<>();
            collectTexts(card().getJSONArray("components"), texts);
            return texts;
        }

        /** All card texts joined by newlines. */
        public String cardText() {
            return String.join("\n", cardTexts());
        }

        /** The title label of the card, without the Markdown heading and link. */
        public String cardTitle() {
            String heading = cardTexts().stream().filter(t -> t.startsWith("### ")).findFirst()
                    .orElseThrow(() -> new AssertionError("No title in " + cardTexts()))
                    .substring(4);
            if (heading.startsWith("[")) {
                heading = heading.substring(1, heading.lastIndexOf("]("));
            }
            return heading.replace("\\[", "[").replace("\\]", "]");
        }

        /** The thumbnail URL of the card header, or {@code null}. */
        public String cardThumbnail() {
            JSONArray children = card().getJSONArray("components");
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                if (child.getInt("type") == SECTION && child.has("accessory")) {
                    return child.getJSONObject("accessory").getJSONObject("media").getString("url");
                }
            }
            return null;
        }

        /** Filenames referenced by the File components of the card. */
        public List<String> cardFiles() {
            List<String> names = new ArrayList<>();
            JSONArray children = card().getJSONArray("components");
            for (int i = 0; i < children.size(); i++) {
                JSONObject child = children.getJSONObject(i);
                if (child.getInt("type") == FILE) {
                    names.add(child.getJSONObject("file").getString("url").replaceFirst("^attachment://", ""));
                }
            }
            return names;
        }

        private static void collectTexts(JSONArray components, List<String> texts) {
            for (int i = 0; i < components.size(); i++) {
                JSONObject component = components.getJSONObject(i);
                if (component.getInt("type") == TEXT_DISPLAY) {
                    texts.add(component.getString("content"));
                }
                if (component.has("components")) {
                    collectTexts(component.getJSONArray("components"), texts);
                }
            }
        }
    }

    /** A canned response. */
    public record Response(int status, String body, Map<String, String> headers) {
    }

    public FakeDiscord() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhooks/" + WEBHOOK_ID + "/" + TOKEN;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public Request only() {
        if (requests.size() != 1) {
            throw new AssertionError("Expected exactly one request but got " + requests.size());
        }
        return requests.get(0);
    }

    public synchronized void enqueue(int status, String body, Map<String, String> headers) {
        queued.add(new Response(status, body, headers));
    }

    public void enqueue(int status, String body) {
        enqueue(status, body, Map.of());
    }

    /**
     * Waits until at least {@code count} requests arrived.
     */
    public void await(int count, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (requests.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        if (requests.size() < count) {
            throw new AssertionError("Expected " + count + " requests but got " + requests.size());
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readAllBytes();
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        List<Part> parts = contentType != null && contentType.startsWith("multipart/form-data")
                ? parseMultipart(body, contentType)
                : List.of();
        Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), query,
                contentType, body, parts, System.currentTimeMillis());
        requests.add(request);

        Response response;
        synchronized (this) {
            response = queued.poll();
        }
        if (response == null) {
            String channel = query.containsKey("thread_id") ? query.get("thread_id")
                    : request.rawPayload().contains("\"thread_name\"") ? NEW_THREAD_ID : CHANNEL_ID;
            response = new Response(200, "{\"id\":\"" + messageIds.incrementAndGet() + "\",\"channel_id\":\"" + channel
                    + "\",\"content\":\"\"}", Map.of());
        }
        response.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] out = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.status(), out.length == 0 ? -1 : out.length);
        if (out.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        }
        exchange.close();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw == null) {
            return query;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return query;
    }

    private static List<Part> parseMultipart(byte[] body, String contentType) {
        Matcher m = BOUNDARY.matcher(contentType);
        if (!m.find()) {
            throw new AssertionError("No boundary in " + contentType);
        }
        byte[] delimiter = ("--" + m.group(1).trim()).getBytes(StandardCharsets.UTF_8);
        List<Part> parts = new ArrayList<>();
        int start = indexOf(body, delimiter, 0);
        while (start >= 0) {
            int partStart = start + delimiter.length;
            if (partStart + 1 < body.length && body[partStart] == '-' && body[partStart + 1] == '-') {
                break;
            }
            partStart += 2; // CRLF after the delimiter
            int next = indexOf(body, delimiter, partStart);
            if (next < 0) {
                throw new AssertionError("Unterminated multipart body");
            }
            byte[] raw = Arrays.copyOfRange(body, partStart, next - 2); // strip CRLF before the delimiter
            int headerEnd = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), 0);
            String headers = new String(raw, 0, headerEnd, StandardCharsets.UTF_8);
            byte[] content = Arrays.copyOfRange(raw, headerEnd + 4, raw.length);
            String name = null;
            String filename = null;
            String type = null;
            for (String header : headers.split("\r\n")) {
                String lower = header.toLowerCase();
                if (lower.startsWith("content-disposition:")) {
                    Matcher nm = NAME.matcher(header);
                    name = nm.find() ? nm.group(1) : null;
                    Matcher fm = FILENAME.matcher(header);
                    filename = fm.find() ? fm.group(1) : null;
                } else if (lower.startsWith("content-type:")) {
                    type = header.substring("content-type:".length()).trim();
                }
            }
            parts.add(new Part(name, filename, type, content));
            start = next;
        }
        return parts;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
