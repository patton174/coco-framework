package io.github.coco.security.jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

final class DefaultCocoJwtAuthoritiesConverter implements CocoJwtAuthoritiesConverter {

    private final JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();

    private final String authoritiesClaim;

    DefaultCocoJwtAuthoritiesConverter(CocoSecurityJwtProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.authoritiesClaim = properties.getAuthoritiesClaim();
        this.delegate.setAuthoritiesClaimName(this.authoritiesClaim);
        this.delegate.setAuthorityPrefix(properties.getAuthoritiesPrefix());
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Jwt checkedJwt = Objects.requireNonNull(jwt, "jwt must not be null");
        Object claim = checkedJwt.getClaim(this.authoritiesClaim);
        if (claim != null && !(claim instanceof String) && !(claim instanceof Collection<?> collection
                && collection.stream().allMatch(String.class::isInstance))) {
            throw new IllegalArgumentException("JWT authorities claim must be a string or a collection of strings");
        }
        Set<String> authorityNames = new LinkedHashSet<>();
        this.delegate.convert(checkedJwt).forEach(authority -> {
            if (authority == null || authority.getAuthority() == null || authority.getAuthority().isBlank()) {
                throw new IllegalArgumentException("JWT authorities claim contains a blank authority");
            }
            authorityNames.add(authority.getAuthority());
        });
        return authorityNames.stream()
                .<GrantedAuthority>map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .toList();
    }
}
