package io.github.coco.feature.httpclient;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.signature.HmacSha256CocoSignatureSigner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 为已序列化请求体生成 Coco HMAC 出站签名的拦截器。
 *
 * @author patton174
 * @since 1.0.0
 */
final class CocoHttpClientSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "proxy-authorization", "cookie");
    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private final String clientName;
    private final CocoHttpClientProperties.Signing signing;
    private final CocoHttpClientSigningCredentialProvider credentialProvider;
    private final CocoWebRequestCanonicalizer canonicalizer;

    CocoHttpClientSigningInterceptor(String clientName, CocoHttpClientProperties.Signing signing,
            CocoHttpClientSigningCredentialProvider credentialProvider, CocoWebRequestCanonicalizer canonicalizer) {
        this.clientName = clientName;
        this.signing = signing;
        this.credentialProvider = credentialProvider;
        this.canonicalizer = canonicalizer;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        rejectConflictingSecurityHeaders(request.getHeaders());
        CocoHttpClientSigningCredential credential = this.credentialProvider.resolve(this.clientName)
                .orElseThrow(() -> new IllegalStateException("Coco HTTP client signing credential is not configured"));
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = nonce();
        applySecurityHeaders(request.getHeaders(), credential, timestamp, nonce);
        CocoWebRequestSecurityInput input = securityInput(request, body);
        CocoWebRequestCanonicalForm canonicalForm = this.canonicalizer.canonicalize(
                new CocoWebRequestCanonicalizationContext(CocoWebRequestCanonicalizationPurpose.SIGNATURE, input,
                        CocoWebRequestSecurityMetadata.empty(), null));
        request.getHeaders().set(this.signing.getSignatureHeaderName(),
                HmacSha256CocoSignatureSigner.sign(credential.algorithm(), canonicalForm.text(), credential.secret()));
        return execution.execute(request, body);
    }

    private CocoWebRequestSecurityInput securityInput(HttpRequest request, byte[] body) {
        URI uri = request.getURI();
        Map<String, List<String>> canonicalHeaders = selectedCanonicalHeaders(request.getHeaders());
        return new CocoWebRequestSecurityInput(request.getMethod().name(), rawPath(uri), uri.getRawQuery(),
                queryParameters(uri.getRawQuery()), Map.of(), Map.of(), Map.of(), join(canonicalHeaders), sha256(body),
                (long) body.length, true, canonicalHeaders, Map.of());
    }

    private void applySecurityHeaders(HttpHeaders headers, CocoHttpClientSigningCredential credential,
            String timestamp, String nonce) {
        headers.set(this.signing.getAppIdHeaderName(), credential.appId());
        headers.set(this.signing.getKeyIdHeaderName(), credential.keyId());
        headers.set(this.signing.getTimestampHeaderName(), timestamp);
        headers.set(this.signing.getNonceHeaderName(), nonce);
        headers.set(this.signing.getAlgorithmHeaderName(), credential.algorithm());
    }

    private void rejectConflictingSecurityHeaders(HttpHeaders headers) {
        for (String name : List.of(this.signing.getAppIdHeaderName(), this.signing.getKeyIdHeaderName(),
                this.signing.getTimestampHeaderName(), this.signing.getNonceHeaderName(),
                this.signing.getSignatureHeaderName(), this.signing.getAlgorithmHeaderName())) {
            if (headers.containsHeader(name)) {
                throw new IllegalStateException("Coco HTTP client signing header is already configured: " + name);
            }
        }
    }

    private Map<String, List<String>> selectedCanonicalHeaders(HttpHeaders headers) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String headerName : this.signing.getCanonicalHeaderNames()) {
            if (SENSITIVE_HEADERS.contains(headerName)) continue;
            List<String> headerValues = headers.get(headerName);
            if (headerValues != null && !headerValues.isEmpty()) values.put(headerName, List.copyOf(headerValues));
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> join(Map<String, List<String>> values) {
        Map<String, String> joined = new LinkedHashMap<>();
        values.forEach((name, value) -> joined.put(name, String.join(",", value)));
        return Map.copyOf(joined);
    }

    private static Map<String, List<String>> queryParameters(String query) {
        if (query == null || query.isBlank()) return Map.of();
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (String pair : query.split("&", -1)) {
            if (pair.isBlank()) continue;
            int index = pair.indexOf('=');
            String name = index < 0 ? pair : pair.substring(0, index);
            String value = index < 0 ? "" : pair.substring(index + 1);
            if (!name.isBlank()) parameters.computeIfAbsent(name, ignored -> new java.util.ArrayList<>()).add(value);
        }
        return Map.copyOf(parameters);
    }

    private static String rawPath(URI uri) {
        String rawPath = uri.getRawPath();
        return rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static String nonce() {
        byte[] bytes = new byte[32];
        NONCE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
