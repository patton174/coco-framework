package io.github.coco.security.spring;

import java.util.Objects;

import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;

final class CocoSpringSecurityBridgeMarker {

    private final CocoWebSecurityContextResolver resolver;

    CocoSpringSecurityBridgeMarker(CocoWebSecurityContextResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    CocoWebSecurityContextResolver resolver() {
        return this.resolver;
    }
}
