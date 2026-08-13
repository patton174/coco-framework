package io.github.coco.security.spring;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

final class DefaultCocoSpringSecurityPrincipalConverter implements CocoSpringSecurityPrincipalConverter {

    private final String rolePrefix;

    DefaultCocoSpringSecurityPrincipalConverter(CocoSpringSecurityProperties properties) {
        this.rolePrefix = Objects.requireNonNull(properties, "properties must not be null").getRolePrefix();
    }

    @Override
    public CocoSecurityPrincipal convert(Authentication authentication) {
        Authentication checkedAuthentication = Objects.requireNonNull(authentication, "authentication must not be null");
        String name = requireName(checkedAuthentication.getName());
        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        if (checkedAuthentication.getAuthorities() != null) {
            checkedAuthentication.getAuthorities().stream()
                    .filter(Objects::nonNull)
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority != null && !authority.isBlank())
                    .map(String::trim)
                    .forEach(authority -> collectAuthority(authority, roles, permissions));
        }
        return new CocoSecurityPrincipal(name, name, Set.copyOf(roles), Set.copyOf(permissions), Map.of());
    }

    private void collectAuthority(String authority, Set<String> roles, Set<String> permissions) {
        if (authority.startsWith(this.rolePrefix) && authority.length() > this.rolePrefix.length()) {
            roles.add(authority.substring(this.rolePrefix.length()));
            return;
        }
        permissions.add(authority);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Spring Security authentication name must not be blank");
        }
        return name.trim();
    }
}
