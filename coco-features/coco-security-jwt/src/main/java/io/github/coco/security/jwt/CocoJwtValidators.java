package io.github.coco.security.jwt;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;

final class CocoJwtValidators {

    private CocoJwtValidators() {
    }

    static OAuth2TokenValidator<Jwt> create(CocoSecurityJwtProperties properties) {
        return create(properties, Clock.systemUTC());
    }

    static OAuth2TokenValidator<Jwt> create(CocoSecurityJwtProperties properties, Clock clock) {
        CocoSecurityJwtProperties checkedProperties = Objects.requireNonNull(properties,
                "properties must not be null");
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(checkedProperties.getClockSkew());
        timestampValidator.setClock(Objects.requireNonNull(clock, "clock must not be null"));
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtTypeValidator.jwt());
        validators.add(timestampValidator);
        if (checkedProperties.getIssuerUri() != null) {
            validators.add(new JwtIssuerValidator(checkedProperties.getIssuerUri().toString()));
        }
        if (!checkedProperties.getAudiences().isEmpty()) {
            validators.add(new JwtClaimValidator<>("aud", audiences -> audiences instanceof Collection<?> values
                    && values.stream().filter(String.class::isInstance).map(String.class::cast)
                            .anyMatch(checkedProperties.getAudiences()::contains)));
        }
        validators.add(new JwtClaimValidator<>(checkedProperties.getPrincipalIdClaim(),
                value -> value instanceof String stringValue && !stringValue.isBlank()));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
