package studio.aakar.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads a Server-Sent Events stream frame by frame while it stays open (order streams only close on
 * delivered/cancelled), so tests can assert the replay and then the live events that follow.
 */
public final class SseClient implements AutoCloseable {

    /** One dispatched event. */
    public record Frame(Integer id, String event, JsonNode data) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpResponse<InputStream> response;
    private final BufferedReader reader;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Frame> collected = new ArrayList<>();
    private boolean eof;

    private SseClient(HttpResponse<InputStream> response) {
        this.response = response;
        this.reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
    }

    /** @param headers alternating name/value pairs */
    public static SseClient open(String url, String lastEventId, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        for (int i = 0; i + 1 < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        try {
            HttpResponse<InputStream> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new SseClient(response);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public int status() {
        return response.statusCode();
    }

    public String contentType() {
        return response.headers().firstValue("Content-Type").orElse("");
    }

    /**
     * Reads until {@code count} more events have arrived or the stream ended. Fails with what arrived when the
     * timeout passes first.
     */
    public List<Frame> read(int count, Duration timeout) {
        List<Frame> batch = new ArrayList<>();
        if (eof) {
            return batch;
        }
        Future<?> task = executor.submit(() -> {
            String id = null;
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while (batch.size() < count && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        Frame frame = new Frame(id == null ? null : Integer.parseInt(id.trim()), event, JSON.readTree(data.toString()));
                        batch.add(frame);
                        synchronized (collected) {
                            collected.add(frame);
                        }
                    }
                    id = null;
                    event = null;
                    data.setLength(0);
                } else if (line.startsWith(":")) {
                    continue; // keep-alive comment
                } else if (line.startsWith("id:")) {
                    id = line.substring(3);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                }
            }
            if (batch.size() < count) {
                eof = true;
            }
            return null;
        });
        try {
            task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new AssertionError("Timed out waiting for " + count + " SSE event(s); got " + batch + " (all so far: " + all() + ")");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return batch;
    }

    /** Everything read on this connection so far. */
    public List<Frame> all() {
        synchronized (collected) {
            return List.copyOf(collected);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            response.body().close();
        } catch (IOException ignored) {
            // the server may already have completed the stream
        }
    }
}
