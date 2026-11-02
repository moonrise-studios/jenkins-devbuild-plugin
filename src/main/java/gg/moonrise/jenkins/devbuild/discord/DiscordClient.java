package gg.moonrise.jenkins.devbuild.discord;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ProxyConfiguration;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Executes Discord webhooks over the JDK HTTP client configured with Jenkins' proxy settings.
 *
 * <p>Requests use {@code wait=true} so Discord returns the created message. HTTP 429 is retried after the delay
 * Discord asks for, 5xx and connection failures are retried with a short backoff, and 401/403/404 fail immediately.
 * The webhook URL is never written to the log or to exception messages.
 */
public final class DiscordClient implements AutoCloseable {

    /** Longest rate limit wait we accept; anything longer fails rather than holding the build. */
    public static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(60);

    private static final String USER_AGENT =
            "DiscordBot (https://github.com/moonrise-studios/jenkins-devbuild-plugin, 1.0)";
    private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(1);

    private final HttpClient httpClient;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final PrintStream log;

    /**
     * @param timeout      connect timeout and per-request response timeout
     * @param maxRetries   retries after the first attempt for 429, 5xx and connection failures
     * @param retryBackoff base delay for 5xx and connection failure retries (doubles per attempt)
     * @param log          build log or other sink for progress messages
     */
    public DiscordClient(@NonNull Duration timeout, int maxRetries, @NonNull Duration retryBackoff, @NonNull PrintStream log) {
        this.timeout = timeout;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoff = retryBackoff;
        this.log = log;
        this.httpClient = ProxyConfiguration.newHttpClientBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public DiscordClient(@NonNull Duration timeout, int maxRetries, @NonNull PrintStream log) {
        this(timeout, maxRetries, DEFAULT_BACKOFF, log);
    }

    /**
     * Executes the webhook and returns the created message.
     *
     * @param webhookUrl webhook URL including the token
     * @param threadId   thread or forum post to post into, or {@code null}
     * @param message    the message and its attachments
     * @throws DiscordException when Discord rejects the request
     * @throws IOException      when the request cannot be sent after all retries
     */
    @NonNull
    public DiscordResponse execute(@NonNull String webhookUrl, @CheckForNull String threadId, @NonNull WebhookMessage message)
            throws IOException, InterruptedException {
        String masked = WebhookUrls.mask(webhookUrl);
        URI uri;
        try {
            uri = buildUri(webhookUrl, threadId);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid Discord webhook URL " + masked);
        }
        String payload = Json.write(message.toPayload());
        MultipartBody multipart = message.getAttachments().isEmpty()
                ? null
                : MultipartBody.create(payload, message.getAttachments());

        for (int attempt = 0; ; attempt++) {
            HttpRequest request = buildRequest(uri, payload, multipart);
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                String reason = WebhookUrls.redact(String.valueOf(e.getMessage()), webhookUrl);
                if (attempt >= maxRetries) {
                    throw new IOException("Could not reach Discord webhook " + masked + ": "
                            + e.getClass().getSimpleName() + ": " + reason);
                }
                Duration wait = backoff(attempt);
                log.printf("[DevBuild] Request to %s failed (%s: %s), retrying in %d ms%n",
                        masked, e.getClass().getSimpleName(), reason, wait.toMillis());
                Thread.sleep(wait.toMillis());
                continue;
            }

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return DiscordResponse.parse(response.body());
            }
            if (status == 429 && attempt < maxRetries) {
                Duration wait = retryAfter(response);
                if (wait.compareTo(MAX_RATE_LIMIT_WAIT) > 0) {
                    throw new DiscordException(status, "Discord rate limited webhook " + masked + " for "
                            + wait.toSeconds() + " s, longer than the " + MAX_RATE_LIMIT_WAIT.toSeconds()
                            + " s we are willing to wait.");
                }
                log.printf("[DevBuild] Rate limited by Discord, retrying in %d ms (attempt %d of %d)%n",
                        wait.toMillis(), attempt + 1, maxRetries);
                Thread.sleep(wait.toMillis());
                continue;
            }
            if (status >= 500 && attempt < maxRetries) {
                Duration wait = backoff(attempt);
                log.printf("[DevBuild] Discord returned HTTP %d, retrying in %d ms%n", status, wait.toMillis());
                Thread.sleep(wait.toMillis());
                continue;
            }
            throw DiscordException.forStatus(status, WebhookUrls.redact(response.body(), webhookUrl), masked);
        }
    }

    /**
     * Appends {@code wait=true} and the optional {@code thread_id} to the webhook URL.
     */
    @NonNull
    public static URI buildUri(@NonNull String webhookUrl, @CheckForNull String threadId) {
        String base = webhookUrl.trim();
        StringBuilder url = new StringBuilder(base);
        url.append(base.contains("?") ? '&' : '?').append("wait=true");
        if (threadId != null && !threadId.isBlank()) {
            url.append("&thread_id=").append(URLEncoder.encode(threadId.trim(), StandardCharsets.UTF_8));
        }
        return URI.create(url.toString());
    }

    private HttpRequest buildRequest(URI uri, String payload, @CheckForNull MultipartBody multipart) {
        HttpRequest.Builder builder = ProxyConfiguration.newHttpRequestBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json");
        if (multipart == null) {
            builder.header("Content-Type", "application/json; charset=UTF-8")
                    .POST(BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        } else {
            builder.header("Content-Type", multipart.contentType()).POST(multipart.publisher());
        }
        return builder.build();
    }

    private Duration backoff(int attempt) {
        long millis = retryBackoff.toMillis() << Math.min(attempt, 5);
        return Duration.ofMillis(millis);
    }

    private static Duration retryAfter(HttpResponse<String> response) {
        Optional<Duration> fromBody = parseRetryAfterBody(response.body());
        if (fromBody.isPresent()) {
            return fromBody.get();
        }
        Optional<String> header = response.headers().firstValue("Retry-After");
        if (header.isPresent()) {
            try {
                return secondsToDuration(Double.parseDouble(header.get().trim()));
            } catch (NumberFormatException e) {
                // fall through to the default delay
            }
        }
        return DEFAULT_BACKOFF;
    }

    private static Optional<Duration> parseRetryAfterBody(@CheckForNull String body) {
        if (body == null || !body.trim().startsWith("{")) {
            return Optional.empty();
        }
        try {
            JSONObject json = JSONObject.fromObject(body);
            if (!json.has("retry_after")) {
                return Optional.empty();
            }
            double seconds = json.getDouble("retry_after");
            return Optional.of(secondsToDuration(seconds));
        } catch (JSONException e) {
            return Optional.empty();
        }
    }

    private static Duration secondsToDuration(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis((long) Math.ceil(seconds * 1000.0));
    }

    @Override
    public void close() {
        httpClient.close();
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "DiscordClient[timeout=%s, maxRetries=%d]", timeout, maxRetries);
    }
}
