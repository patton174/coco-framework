package io.github.coco.feature.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 默认 HTTP 错误映射器。
 */
public final class DefaultCocoHttpErrorMapper implements CocoHttpErrorMapper {

    static final int MAX_RESPONSE_BYTES = 1024;
    static final int MAX_RESPONSE_CHARACTERS = 512;

    @Override
    public RuntimeException map(String clientName, HttpRequest request, ClientHttpResponse response) throws IOException {
        return new CocoHttpClientException(clientName, request.getMethod(), sanitize(request.getURI()),
                response.getStatusCode(), summarize(response.getBody()));
    }

    private static String sanitize(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception ex) {
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
        }
    }

    private static String summarize(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
        boolean truncated = bytes.length > MAX_RESPONSE_BYTES;
        int length = Math.min(bytes.length, MAX_RESPONSE_BYTES);
        String summary = new String(bytes, 0, length, StandardCharsets.UTF_8);
        if (summary.length() > MAX_RESPONSE_CHARACTERS) {
            summary = summary.substring(0, MAX_RESPONSE_CHARACTERS);
            truncated = true;
        }
        return truncated ? summary + "..." : summary;
    }
}
