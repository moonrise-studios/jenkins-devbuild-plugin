package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds a {@code multipart/form-data} body with a {@code payload_json} part and one {@code files[n]} part per
 * attachment. File content is streamed from its source while the request is sent; nothing is buffered in memory.
 */
public final class MultipartBody {

    private static final String CRLF = "\r\n";

    private final String boundary;
    private final List<BodyPublisher> parts = new ArrayList<>();
    private long contentLength;

    private MultipartBody(String boundary) {
        this.boundary = boundary;
    }

    /**
     * Creates the body for a payload and its attachments.
     */
    @NonNull
    public static MultipartBody create(@NonNull String payloadJson, @NonNull List<DiscordAttachment> attachments) {
        MultipartBody body = new MultipartBody("DevBuild-" + UUID.randomUUID().toString().replace("-", ""));
        body.addText("--" + body.boundary + CRLF
                + "Content-Disposition: form-data; name=\"payload_json\"" + CRLF
                + "Content-Type: application/json; charset=UTF-8" + CRLF + CRLF);
        body.addText(payloadJson);
        body.addText(CRLF);
        for (int i = 0; i < attachments.size(); i++) {
            DiscordAttachment attachment = attachments.get(i);
            body.addText("--" + body.boundary + CRLF
                    + "Content-Disposition: form-data; name=\"files[" + i + "]\"; filename=\""
                    + headerSafe(attachment.filename()) + "\"" + CRLF
                    + "Content-Type: " + contentTypeOf(attachment.filename()) + CRLF + CRLF);
            body.addStream(attachment);
            body.addText(CRLF);
        }
        body.addText("--" + body.boundary + "--" + CRLF);
        return body;
    }

    @NonNull
    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public long contentLength() {
        return contentLength;
    }

    /**
     * Returns a new publisher over the body. Every call opens the files again, so it is safe to call once per attempt.
     */
    @NonNull
    public BodyPublisher publisher() {
        BodyPublisher concatenated = BodyPublishers.concat(parts.toArray(new BodyPublisher[0]));
        return BodyPublishers.fromPublisher(concatenated, contentLength);
    }

    private void addText(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        parts.add(BodyPublishers.ofByteArray(bytes));
        contentLength += bytes.length;
    }

    private void addStream(DiscordAttachment attachment) {
        parts.add(HttpRequest.BodyPublishers.ofInputStream(() -> open(attachment)));
        contentLength += attachment.size();
    }

    private static InputStream open(DiscordAttachment attachment) {
        try {
            return attachment.source().open();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new InterruptedIOException("Interrupted while opening " + attachment.filename()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String headerSafe(String filename) {
        StringBuilder out = new StringBuilder(filename.length());
        for (char c : filename.toCharArray()) {
            if (c == '"' || c == '\\' || c == '\r' || c == '\n') {
                out.append('_');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String contentTypeOf(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jar")) {
            return "application/java-archive";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}
