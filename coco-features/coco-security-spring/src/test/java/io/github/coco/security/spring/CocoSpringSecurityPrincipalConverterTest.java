package io.github.coco.security.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class CocoSpringSecurityPrincipalConverterTest {

    @Test
    void defaultConverterMapsOnlyNameAndAuthorities() {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator-42", "credential-that-must-not-leak", List.of(
                        new SimpleGrantedAuthority("invoice:read"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("audit:read"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails("details-that-must-not-leak");

        CocoSecurityPrincipal principal = new DefaultCocoSpringSecurityPrincipalConverter(
                new CocoSpringSecurityProperties()).convert(authentication);

        assertThat(principal.principalId()).isEqualTo("operator-42");
        assertThat(principal.principalName()).isEqualTo("operator-42");
        assertThat(principal.roles()).containsExactlyInAnyOrder("ADMIN");
        assertThat(principal.permissions()).containsExactlyInAnyOrder("invoice:read", "audit:read");
        assertThat(principal.attributes()).isEmpty();
        assertThat(principal.attributes().values()).doesNotContain(
                authentication.getCredentials(), authentication.getDetails(), authentication.getPrincipal());
    }

    @Test
    void converterFailsClosedForBlankNameAndIgnoresInvalidAuthorities() {
        CocoSpringSecurityPrincipalConverter converter = new DefaultCocoSpringSecurityPrincipalConverter(
                new CocoSpringSecurityProperties());
        Authentication blankName = new TestingAuthenticationToken("ignored", "credential") {
            @Override
            public String getName() {
                return "  ";
            }
        };
        Authentication invalidAuthorities = new TestingAuthenticationToken("operator", "credential") {
            @Override
            public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return Arrays.asList(null, () -> null, () -> " ", () -> "ROLE_", () -> " permission ");
            }
        };

        assertThatThrownBy(() -> converter.convert(blankName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spring Security authentication name must not be blank");
        CocoSecurityPrincipal principal = converter.convert(invalidAuthorities);
        assertThat(principal.roles()).isEmpty();
        assertThat(principal.permissions()).containsExactlyInAnyOrder("ROLE_", "permission");
        assertThatThrownBy(() -> principal.permissions().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
