package io.github.coco.feature.concurrencylimit.redis;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CocoConcurrencyLimitRedisPropertiesTest {

    @Test
    void validatesDerivedApplicationNamespaceAndLeaseRelationship() {
        CocoConcurrencyLimitRedisProperties properties = new CocoConcurrencyLimitRedisProperties();
        properties.validate("orders-api");

        properties.setRenewInterval(Duration.ofSeconds(15));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.validate("orders-api"))
                .withMessageContaining("renew-interval");
    }

    @Test
    void rejectsUnsafePrefixAndNamespace() {
        CocoConcurrencyLimitRedisProperties properties = new CocoConcurrencyLimitRedisProperties();
        properties.setKeyPrefix("coco:{unsafe}:");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.validate("orders-api"));

        properties.setKeyPrefix("coco:limit:");
        properties.setAppNamespace("orders {*} api");
        assertThatIllegalArgumentException().isThrownBy(() -> properties.validate("orders-api"));
    }
}
