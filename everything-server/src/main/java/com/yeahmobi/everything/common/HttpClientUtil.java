package com.yeahmobi.everything.common;

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
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * HTTP utility class wrapping Java 11+ HttpClient for GET and POST requests.
 */
public final class HttpClientUtil {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Creates an HttpClientUtil with default settings.
     */
    public HttpClientUtil() {
        this(DEFAULT_TIMEOUT);
    }

    /**
     * Creates an HttpClientUtil with a custom timeout.
     *
     * @param timeout the request timeout duration
     */
    public HttpClientUtil(Duration timeout) {
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Creates an HttpClientUtil with a custom HttpClient (for testing).
     *
     * @param httpClient the HttpClient instance to use
     * @param timeout    the request timeout duration
     */
    HttpClientUtil(HttpClient httpClient, Duration timeout) {
        this.httpClient = httpClient;
        this.timeout = timeout;
    }

    /**
     * Sends a GET request.
     *
     * @param url     the request URL
     * @param headers optional headers as key-value pairs
     * @return the response body as a string
     * @throws NetworkException if the request fails
     */
    public String get(String url, Map<String, String> headers) throws NetworkException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .GET();

            if (headers != null) {
                headers.forEach(builder::header);
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new NetworkException(
                        "HTTP GET failed with status " + response.statusCode() + ": " + response.body()
                );
            }

            return response.body();
        } catch (NetworkException e) {
            throw e;
        } catch (IOException e) {
            throw new NetworkException("Network error during GET request to " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("GET request to " + url + " was interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new NetworkException("Invalid URL: " + url, e);
        }
    }

    /**
     * Sends a POST request with a JSON body.
     *
     * @param url      the request URL
     * @param jsonBody the JSON request body
     * @param headers  optional headers as key-value pairs
     * @return the response body as a string
     * @throws NetworkException if the request fails
     */
    public String post(String url, String jsonBody, Map<String, String> headers) throws NetworkException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (headers != null) {
                headers.forEach(builder::header);
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new NetworkException(
                        "HTTP POST failed with status " + response.statusCode() + ": " + response.body()
                );
            }

            return response.body();
        } catch (NetworkException e) {
            throw e;
        } catch (IOException e) {
            String detailMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new NetworkException("Network error during POST request to " + url + " - " + detailMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("POST request to " + url + " was interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new NetworkException("Invalid URL: " + url, e);
        }
    }

    /**
     * Sends a POST request and processes Server-Sent Events (SSE) stream.
     *
     * @param url      the request URL
     * @param jsonBody the JSON request body
     * @param headers  optional headers as key-value pairs
     * @param callback callback that receives (event, data) for each SSE message
     * @throws NetworkException if the request fails
     */
    public void postStream(String url, String jsonBody, Map<String, String> headers,
                          BiConsumer<String, String> callback) throws NetworkException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5)) // Longer timeout for streaming
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (headers != null) {
                headers.forEach(builder::header);
            }

            HttpResponse<InputStream> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() >= 400) {
                throw new NetworkException(
                        "HTTP POST stream failed with status " + response.statusCode()
                );
            }

            // Parse SSE stream
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                String currentEvent = "message";
                StringBuilder dataBuffer = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        if (dataBuffer.length() > 0) {
                            dataBuffer.append("\n");
                        }
                        dataBuffer.append(line.substring(5).trim());
                    } else if (line.isEmpty()) {
                        // End of message
                        if (dataBuffer.length() > 0) {
                            callback.accept(currentEvent, dataBuffer.toString());
                            dataBuffer.setLength(0);
                            currentEvent = "message";
                        }
                    }
                }
            }
        } catch (NetworkException e) {
            throw e;
        } catch (IOException e) {
            String detailMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new NetworkException("Network error during POST stream to " + url + " - " + detailMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("POST stream to " + url + " was interrupted", e);
        } catch (IllegalArgumentException e) {
            throw new NetworkException("Invalid URL: " + url, e);
        }
    }
}
