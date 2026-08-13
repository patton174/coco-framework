package io.github.coco.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CocoJwtConvertersTest {

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesScopeExtractionToSpringSecurity() {
        Jwt jwt = jwt().claim("scope", "orders.read orders.write").build();

        assertThat(new DefaultCocoJwtAuthoritiesConverter(new CocoSecurityJwtProperties()).convert(jwt))
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("SCOPE_orders.read", "SCOPE_orders.write");
    }

    @Test
    void appliesConfiguredAuthoritiesClaimPrefixAndDeduplicates() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        properties.setAuthoritiesClaim("roles");
        properties.setAuthoritiesPrefix("ROLE_");
        Jwt jwt = jwt().claim("roles", List.of("admin", "admin", "auditor")).build();

        assertThat(new DefaultCocoJwtAuthoritiesConverter(properties).convert(jwt))
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_admin", "ROLE_auditor");
    }

    @Test
    void rejectsIllegalAuthoritiesClaimType() {
        Jwt jwt = jwt().claim("scope", List.of("orders.read", 42)).build();

        assertThatThrownBy(() -> new DefaultCocoJwtAuthoritiesConverter(new CocoSecurityJwtProperties())
                .convert(jwt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT authorities claim must be a string or a collection of strings");
    }

    @Test
    void mapsJwtAndAuthoritiesToProtocolNeutralCocoPrincipal() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        CocoJwtPrincipalConverter converter = new DefaultCocoJwtPrincipalConverter(properties);
        Jwt jwt = jwt().subject("1001").claim("name", "Patton").claim("department", "platform")
                .claim("email", "patton@example.com").claim("access_token", "secret").build();

        CocoSecurityPrincipal principal = converter.convert(jwt, List.of(
                new SimpleGrantedAuthority("ROLE_admin"),
                new SimpleGrantedAuthority("SCOPE_order:read"),
                new SimpleGrantedAuthority("audit:read")));

        assertThat(principal.principalId()).isEqualTo("1001");
        assertThat(principal.principalName()).isEqualTo("Patton");
        assertThat(principal.roles()).containsExactly("admin");
        assertThat(principal.permissions()).containsExactlyInAnyOrder("order:read", "audit:read");
        assertThat(principal.attributes()).isEmpty();
    }

    @Test
    void onlyConfiguredClaimAllowlistIsPropagatedToPrincipalAttributes() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        properties.setPrincipalAttributeClaims(List.of("department"));
        CocoJwtPrincipalConverter converter = new DefaultCocoJwtPrincipalConverter(properties);
        Jwt jwt = jwt().subject("1001").claim("department", "platform")
                .claim("email", "patton@example.com").claim("access_token", "secret").build();

        CocoSecurityPrincipal principal = converter.convert(jwt, List.of());

        assertThat(principal.attributes()).containsExactlyEntriesOf(java.util.Map.of("department", "platform"));
        assertThat(principal.attributes()).doesNotContainKeys("email", "access_token", "token");
        assertThat(principal.attributes()).doesNotContainValue(jwt.getTokenValue());
    }

    @Test
    void rejectsTokenClaimNamesInAttributeAllowlist() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        properties.setEnabled(true);
        properties.setIssuerUri(java.net.URI.create("https://idp.example.com/issuer"));
        properties.setPrincipalAttributeClaims(List.of("access_token"));

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("coco.security.jwt.principal-attribute-claims must not include token values");
    }

    @Test
    void resolvesAuthenticatedSpringJwtToCocoContext() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        CocoJwtWebSecurityContextResolver resolver = new CocoJwtWebSecurityContextResolver(
                new DefaultCocoJwtPrincipalConverter(properties));
        Jwt jwt = jwt().subject("1001").claim("name", "Patton").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_order:read"))));

        Optional<CocoSecurityContext> resolved = resolver.resolve(new MockHttpServletRequest("GET", "/orders"));

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().principal().principalId()).isEqualTo("1001");
        assertThat(resolved.orElseThrow().principal().permissions()).contains("order:read");
    }

    @Test
    void doesNotResolveUnverifiedJwtAuthentication() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        CocoJwtWebSecurityContextResolver resolver = new CocoJwtWebSecurityContextResolver(
                new DefaultCocoJwtPrincipalConverter(properties));
        Jwt jwt = jwt().subject("1001").build();
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(jwt, "ignored");
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolver.resolve(new MockHttpServletRequest("GET", "/orders"))).isEmpty();
    }

    @Test
    void rejectsNonStringPrincipalClaims() {
        CocoSecurityJwtProperties properties = new CocoSecurityJwtProperties();
        CocoJwtPrincipalConverter converter = new DefaultCocoJwtPrincipalConverter(properties);

        assertThatThrownBy(() -> converter.convert(jwt().claim("sub", List.of("1001")).build(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT claim sub must not be blank");
    }

    private static Jwt.Builder jwt() {
        Instant now = Instant.parse("2026-08-08T08:00:00Z");
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(300));
    }
}
