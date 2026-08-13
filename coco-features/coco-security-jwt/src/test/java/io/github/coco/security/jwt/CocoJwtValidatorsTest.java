package io.github.coco.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class CocoJwtValidatorsTest {

    private static final Instant NOW = Instant.parse("2026-08-08T08:00:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void rejectsWrongIssuer() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(60));
        Jwt jwt = jwt("https://wrong.example.com", List.of("orders-api"), NOW.plusSeconds(60), "user-1");

        assertThat(validator(properties).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingConfiguredAudience() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(60));
        properties.setAudiences(List.of("orders-api", "internal-api"));
        Jwt jwt = jwt("https://idp.example.com/issuer", List.of("other-api"), NOW.plusSeconds(60), "user-1");

        assertThat(validator(properties).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsAudienceWithIllegalClaimType() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(60));
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(NOW.minusSeconds(30))
                .expiresAt(NOW.plusSeconds(60))
                .issuer("https://idp.example.com/issuer")
                .claim("aud", 42)
                .subject("user-1")
                .build();

        assertThat(validator(properties).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void acceptsAnyAudienceFromConfiguredAllowlist() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(60));
        properties.setAudiences(List.of("orders-api", "internal-api"));
        Jwt jwt = jwt("https://idp.example.com/issuer", List.of("internal-api"), NOW.plusSeconds(60), "user-1");

        assertThat(validator(properties).validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenNotValidBeyondClockSkew() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(30));
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(NOW.minusSeconds(300))
                .notBefore(NOW.plusSeconds(31))
                .expiresAt(NOW.plusSeconds(300))
                .issuer("https://idp.example.com/issuer")
                .audience(List.of("orders-api"))
                .subject("user-1")
                .build();

        assertThat(validator(properties).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenExpiredBeyondClockSkew() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(60));
        Jwt jwt = jwt("https://idp.example.com/issuer", List.of("orders-api"), NOW.minusSeconds(61), "user-1");

        assertThat(validator(properties).validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void appliesConfiguredClockSkewBoundary() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(30));
        Jwt withinSkew = jwt("https://idp.example.com/issuer", List.of("orders-api"),
                NOW.minusSeconds(30), "user-1");
        Jwt beyondSkew = jwt("https://idp.example.com/issuer", List.of("orders-api"),
                NOW.minusSeconds(31), "user-1");

        assertThat(validator(properties).validate(withinSkew).hasErrors()).isFalse();
        assertThat(validator(properties).validate(beyondSkew).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingPrincipalIdClaim() {
        CocoSecurityJwtProperties properties = properties(Duration.ofSeconds(60));
        Jwt jwt = jwt("https://idp.example.com/issuer", List.of("orders-api"), NOW.plusSeconds(60), null);

        assertThat(validator(properties).validate(jwt).hasErrors()).isTrue();
    }

    private static OAuth2TokenValidator<Jwt> validator(CocoSecurityJwtProperties properties) {
        return CocoJwtValidators.create(properties, CLOCK);
    }

    private static CocoSecurityJwtProperties properties(Duration clockSkew) {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        properties.setEnabled(true);
        properties.setIssuerUri(URI.create("https://idp.example.com/issuer"));
        properties.setAudiences(List.of("orders-api"));
        properties.setClockSkew(clockSkew);
        properties.afterPropertiesSet();
        return properties;
    }

    private static Jwt jwt(String issuer, List<String> audiences, Instant expiresAt, String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(NOW.minusSeconds(300))
                .expiresAt(expiresAt)
                .issuer(issuer)
                .audience(audiences)
                .claim("name", "Test User");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
