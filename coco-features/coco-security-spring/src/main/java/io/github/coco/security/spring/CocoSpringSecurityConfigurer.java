package io.github.coco.security.spring;

import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Adds the Coco security-context bridge inside every Spring Security filter chain.
 * <p>
 * Spring Security discovers this configurer through {@code META-INF/spring.factories}. The configurer is a no-op
 * unless the Coco Spring Security auto-configuration owns the default bridge marker, so merely placing this module
 * on the classpath does not change a business security chain.
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSpringSecurityConfigurer
        extends AbstractHttpConfigurer<CocoSpringSecurityConfigurer, HttpSecurity> {

    /**
     * Creates the default configurer loaded by Spring Security.
     */
    public CocoSpringSecurityConfigurer() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure(HttpSecurity http) {
        ApplicationContext applicationContext = http.getSharedObject(ApplicationContext.class);
        if (applicationContext == null) {
            return;
        }
        CocoSpringSecurityBridgeMarker marker = applicationContext
                .getBeanProvider(CocoSpringSecurityBridgeMarker.class)
                .getIfAvailable();
        if (marker == null) {
            return;
        }
        http.addFilterAfter(new CocoSecurityWebFilter(marker.resolver()), AnonymousAuthenticationFilter.class);
    }
}
