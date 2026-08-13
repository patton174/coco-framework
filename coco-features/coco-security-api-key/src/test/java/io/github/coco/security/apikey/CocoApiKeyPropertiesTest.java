package io.github.coco.security.apikey;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CocoApiKeyPropertiesTest {

    @Test
    void validatesEnabledConfigurationWithoutReportingDigest() {
        CocoApiKeyProperties properties = CocoApiKeyWebSecurityContextResolverTest.enabledProperties();
        CocoApiKeyProperties.Credential credential = new CocoApiKeyProperties.Credential();
        credential.setSha256("not-a-digest");
        credential.setPrincipalId("service");
        properties.setCredentials(java.util.Map.of("service", credential));

        assertThatThrownBy(properties::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid sha256")
                .hasMessageNotContaining("not-a-digest");
    }

    @Test
    void rejectsReservedAndMalformedHeaderNamesAndUnsafeLength() {
        for (String headerName : java.util.List.of("Authorization", "Cookie", "Proxy-Authorization", "bad header")) {
            CocoApiKeyProperties properties = CocoApiKeyWebSecurityContextResolverTest.enabledProperties();
            properties.setHeaderName(headerName);
            assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining(headerName);
        }
        CocoApiKeyProperties properties = CocoApiKeyWebSecurityContextResolverTest.enabledProperties();
        properties.setMaxKeyLength(4097);
        assertThatThrownBy(properties::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }
}
