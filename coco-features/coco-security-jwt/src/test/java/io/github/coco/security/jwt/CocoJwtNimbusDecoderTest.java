package io.github.coco.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class CocoJwtNimbusDecoderTest {

    @Test
    void jwkSetUriOverrideStillRejectsWrongIssuerWithNimbusValidation() throws Exception {
        RSAKey signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        try (JwkSetServer server = new JwkSetServer(signingKey)) {
            CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
            properties.setEnabled(true);
            properties.setIssuerUri(URI.create("https://issuer.example.com"));
            properties.setJwkSetUri(server.uri());
            properties.setAudiences(java.util.List.of("orders-api"));
            properties.afterPropertiesSet();
            JwtDecoder decoder = new CocoSecurityJwtSecurityConfiguration().cocoJwtDecoder(properties);

            assertThatThrownBy(() -> decoder.decode(token(signingKey, "https://wrong.example.com")))
                    .isInstanceOf(JwtValidationException.class);
            assertThat(decoder.decode(token(signingKey, "https://issuer.example.com")).getSubject())
                    .isEqualTo("1001");
        }
    }

    @Test
    void rejectsTokenSignedByAnUnknownKey() throws Exception {
        RSAKey publishedKey = new RSAKeyGenerator(2048).keyID("published-key").generate();
        RSAKey unknownKey = new RSAKeyGenerator(2048).keyID("unknown-key").generate();
        try (JwkSetServer server = new JwkSetServer(publishedKey)) {
            CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
            properties.setEnabled(true);
            properties.setJwkSetUri(server.uri());
            properties.afterPropertiesSet();
            JwtDecoder decoder = new CocoSecurityJwtSecurityConfiguration().cocoJwtDecoder(properties);

            assertThatThrownBy(() -> decoder.decode(token(unknownKey, "https://issuer.example.com")))
                    .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        }
    }

    private static String token(RSAKey signingKey, String issuer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("1001")
                .audience(List.of("orders-api"))
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300))
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(signingKey.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static final class JwkSetServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final ExecutorService executor = Executors.newSingleThreadExecutor();

        private final byte[] responseBody;

        private JwkSetServer(RSAKey signingKey) throws IOException {
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            this.responseBody = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            this.executor.submit(this::serve);
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + this.serverSocket.getLocalPort() + "/jwks");
        }

        private void serve() {
            while (!this.serverSocket.isClosed()) {
                try (Socket socket = this.serverSocket.accept()) {
                    writeResponse(socket);
                }
                catch (IOException exception) {
                    if (!this.serverSocket.isClosed()) {
                        throw new IllegalStateException("JWK test endpoint failed", exception);
                    }
                }
            }
        }

        private void writeResponse(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Consume the request headers before writing the static JWK Set response.
            }
            String responseHeaders = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + this.responseBody.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(responseHeaders.getBytes(StandardCharsets.US_ASCII));
            output.write(this.responseBody);
            output.flush();
        }

        @Override
        public void close() throws IOException {
            this.serverSocket.close();
            this.executor.shutdownNow();
            try {
                this.executor.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
