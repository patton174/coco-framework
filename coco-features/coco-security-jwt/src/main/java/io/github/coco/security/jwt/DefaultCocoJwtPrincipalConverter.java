package io.github.coco.security.jwt;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

final class DefaultCocoJwtPrincipalConverter implements CocoJwtPrincipalConverter {

    private static final String ROLE_PREFIX = "ROLE_";

    private static final String SCOPE_PREFIX = "SCOPE_";

    private final String principalIdClaim;

    private final String principalNameClaim;

    private final Set<String> principalAttributeClaims;

    DefaultCocoJwtPrincipalConverter(CocoSecurityJwtProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.principalIdClaim = properties.getPrincipalIdClaim();
        this.principalNameClaim = properties.getPrincipalNameClaim();
        this.principalAttributeClaims = properties.getPrincipalAttributeClaims();
    }

    @Override
    public CocoSecurityPrincipal convert(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        Jwt checkedJwt = Objects.requireNonNull(jwt, "jwt must not be null");
        String principalId = requireClaim(checkedJwt, this.principalIdClaim);
        String principalName = optionalClaim(checkedJwt, this.principalNameClaim, principalId);
        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        if (authorities != null) {
            authorities.stream()
                    .filter(Objects::nonNull)
                    .map(GrantedAuthority::getAuthority)
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(authority -> collectAuthority(authority.trim(), roles, permissions));
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        this.principalAttributeClaims.forEach(name -> {
            Object value = checkedJwt.getClaim(name);
            if (value != null) {
                attributes.put(name, value);
            }
        });
        return new CocoSecurityPrincipal(principalId, principalName, roles, permissions, attributes);
    }

    private static void collectAuthority(String authority, Set<String> roles, Set<String> permissions) {
        if (authority.startsWith(ROLE_PREFIX) && authority.length() > ROLE_PREFIX.length()) {
            roles.add(authority.substring(ROLE_PREFIX.length()));
            return;
        }
        if (authority.startsWith(SCOPE_PREFIX) && authority.length() > SCOPE_PREFIX.length()) {
            permissions.add(authority.substring(SCOPE_PREFIX.length()));
            return;
        }
        permissions.add(authority);
    }

    private static String requireClaim(Jwt jwt, String claimName) {
        Object claim = jwt.getClaim(claimName);
        if (!(claim instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("JWT claim " + claimName + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalClaim(Jwt jwt, String claimName, String fallback) {
        Object claim = jwt.getClaim(claimName);
        if (claim == null) {
            return fallback;
        }
        if (!(claim instanceof String value)) {
            throw new IllegalArgumentException("JWT claim " + claimName + " must be a string");
        }
        return value.isBlank() ? fallback : value.trim();
    }
}
