package io.github.coco.feature.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RedisCocoLockStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void acquireUsesLuaTtlAndDigestKeyWithoutExposingLogicalKey() throws Exception {
        CapturingExecutor executor = new CapturingExecutor(1L);
        RedisCocoLockStore store = new RedisCocoLockStore(executor, "coco:lock:", CLOCK);
        CocoLockLease lease = lease("tenant:42:billing", "owner-a", 1500);

        assertThat(store.acquire(lease)).isEqualTo(CocoLockStore.AcquireResult.ACQUIRED);
        assertThat(executor.script.getScriptAsString()).contains("SET", "NX", "PX", "return 1", "return 0");
        assertThat(executor.keys).containsExactly("coco:lock:" + sha256("tenant:42:billing"));
        assertThat(executor.keys.get(0)).doesNotContain("tenant:42:billing");
        assertThat(executor.arguments).containsExactly("owner-a", "1500");
    }

    @Test
    void ownerSafeRenewAndReleaseMapScriptContracts() {
        CapturingExecutor executor = new CapturingExecutor(0L);
        RedisCocoLockStore store = new RedisCocoLockStore(executor, "coco:lock:", CLOCK);
        CocoLockLease lease = lease("orders", "owner-a", 2000);

        assertThat(store.renew(lease)).isEqualTo(CocoLockStore.RenewResult.NOT_OWNER);
        assertThat(executor.script.getScriptAsString()).contains("GET", "PEXPIRE", "ARGV[1]", "return 0");
        assertThat(executor.arguments).containsExactly("owner-a", "2000");
        assertThat(store.release(lease)).isFalse();
        assertThat(executor.script.getScriptAsString()).contains("GET", "DEL", "ARGV[1]", "return 0");
        assertThat(store.release(null)).isFalse();
    }

    @Test
    void unavailableRedisAndExpiredLeasesFailClosed() {
        RedisCocoLockStore unavailable = new RedisCocoLockStore((script, keys, arguments) -> {
            throw new IllegalStateException("redis unavailable");
        }, "coco:lock:", CLOCK);
        CocoLockLease active = lease("orders", "owner-a", 1000);

        assertThat(unavailable.acquire(active)).isEqualTo(CocoLockStore.AcquireResult.UNAVAILABLE);
        assertThat(unavailable.renew(active)).isEqualTo(CocoLockStore.RenewResult.UNAVAILABLE);
        assertThat(unavailable.release(active)).isFalse();

        CapturingExecutor executor = new CapturingExecutor(1L);
        RedisCocoLockStore store = new RedisCocoLockStore(executor, "coco:lock:", CLOCK);
        CocoLockLease expired = new CocoLockLease("orders", "owner-a", CLOCK.instant());
        assertThat(store.acquire(expired)).isEqualTo(CocoLockStore.AcquireResult.UNAVAILABLE);
        assertThat(store.renew(expired)).isEqualTo(CocoLockStore.RenewResult.NOT_OWNER);
        assertThat(executor.script).isNull();
    }

    @Test
    void exposesLuaResultTypesForRedisExecution() {
        assertThat(RedisCocoLockStore.acquireScript().getResultType()).isEqualTo(Long.class);
        assertThat(RedisCocoLockStore.renewScript().getResultType()).isEqualTo(Long.class);
        assertThat(RedisCocoLockStore.releaseScript().getResultType()).isEqualTo(Long.class);
    }

    private static CocoLockLease lease(String key, String owner, long ttlMillis) {
        return new CocoLockLease(key, owner, CLOCK.instant().plusMillis(ttlMillis));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class CapturingExecutor implements RedisCocoLockStore.ScriptExecutor {
        private final Long result;
        private DefaultRedisScript<Long> script;
        private List<String> keys;
        private List<Object> arguments;

        private CapturingExecutor(Long result) {
            this.result = result;
        }

        @Override
        public Long execute(DefaultRedisScript<Long> script, List<String> keys, Object... arguments) {
            this.script = script;
            this.keys = List.copyOf(keys);
            this.arguments = List.of(arguments);
            return this.result;
        }
    }
}
