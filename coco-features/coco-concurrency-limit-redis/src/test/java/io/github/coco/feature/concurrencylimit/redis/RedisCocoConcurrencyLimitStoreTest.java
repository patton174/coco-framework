package io.github.coco.feature.concurrencylimit.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitAcquisition;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitConstraint;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitDimension;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitPermit;
import io.github.coco.feature.concurrencylimit.CocoConcurrencyLimitRequest;
import org.junit.jupiter.api.Test;

class RedisCocoConcurrencyLimitStoreTest {

    @Test
    void acquiresAllDimensionsAtomicallyAndNormalizesOrder() {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore store = store(executor);
        CocoConcurrencyLimitRequest first = request(route(1), global(2));
        CocoConcurrencyLimitRequest reordered = request(global(2), route(1));

        assertThat(store.acquire(first).acquired()).isTrue();
        CocoConcurrencyLimitAcquisition rejected = store.acquire(reordered);

        assertThat(rejected.acquired()).isFalse();
        assertThat(rejected.rejectedDimension()).isEqualTo(CocoConcurrencyLimitDimension.ROUTE);
        assertThat(rejected.snapshots()).extracting(snapshot -> snapshot.remaining()).containsExactly(1, 0);
        assertThat(executor.active("route")).isOne();
        assertThat(executor.active("global")).isOne();
    }

    @Test
    void concurrentStoresNeverExceedLimitAndReleaseIsTokenSafe() throws Exception {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore first = store(executor);
        RedisCocoConcurrencyLimitStore second = store(executor);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<StoreAcquisition>> calls = new ArrayList<>();
            for (int index = 0; index < 80; index++) {
                RedisCocoConcurrencyLimitStore store = index % 2 == 0 ? first : second;
                calls.add(() -> new StoreAcquisition(store, store.acquire(request(route(3)))));
            }
            List<StoreAcquisition> acquisitions = pool.invokeAll(calls).stream()
                    .map(future -> { try { return future.get(); } catch (Exception ex) { throw new AssertionError(ex); } })
                    .toList();
            assertThat(acquisitions).filteredOn(value -> value.acquisition().acquired()).hasSize(3);
            StoreAcquisition acquired = acquisitions.stream()
                    .filter(value -> value.acquisition().acquired()).findFirst().orElseThrow();
            RedisCocoConcurrencyLimitStore foreign = acquired.store() == first ? second : first;
            assertThatIllegalArgumentException().isThrownBy(() -> foreign.release(acquired.acquisition().permit()));
            acquired.store().release(acquired.acquisition().permit());
            acquired.store().release(acquired.acquisition().permit());
            assertThat(executor.active("route")).isEqualTo(2);
        }
        finally { pool.shutdownNow(); }
    }

    @Test
    void recoversExpiredCrashLeaseAndRenewKeepsPermitAlive() {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore crashed = store(executor);
        RedisCocoConcurrencyLimitStore other = store(executor);
        assertThat(crashed.acquire(request(route(1))).acquired()).isTrue();
        assertThat(other.acquire(request(route(1))).acquired()).isFalse();
        executor.advance(Duration.ofSeconds(31));
        assertThat(other.acquire(request(route(1))).acquired()).isTrue();

        LeaseExecutor renewExecutor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore renewing = store(renewExecutor);
        RedisCocoConcurrencyLimitStore observer = store(renewExecutor);
        assertThat(renewing.acquire(request(route(1))).acquired()).isTrue();
        renewExecutor.advance(Duration.ofSeconds(20));
        renewing.renewNowForTests();
        renewExecutor.advance(Duration.ofSeconds(20));
        assertThat(observer.acquire(request(route(1))).acquired()).isFalse();
    }

    @Test
    void renewalFailureCanRecoverAndDoesNotBlockRelease() {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore store = store(executor);
        CocoConcurrencyLimitAcquisition acquisition = store.acquire(request(route(1)));
        executor.failNextRenewals = 1;

        store.renewNowForTests();
        store.renewNowForTests();
        store.release(acquisition.permit());

        assertThat(executor.active("route")).isZero();
        assertThat(executor.renewAttempts).isEqualTo(2);
        assertThat(executor.releaseAttempts).isOne();
    }

    @Test
    void renewalFailureDoesNotBlockImmediateRelease() {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore store = store(executor);
        CocoConcurrencyLimitAcquisition acquisition = store.acquire(request(route(1)));
        executor.failNextRenewals = 1;

        store.renewNowForTests();
        store.release(acquisition.permit());

        assertThat(executor.active("route")).isZero();
        assertThat(executor.releaseAttempts).isOne();
    }

    @Test
    void releaseFailureCanBeRetriedByCallerAndClose() {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore callerStore = store(executor);
        CocoConcurrencyLimitAcquisition callerPermit = callerStore.acquire(request(route(2)));
        executor.failNextReleases = 1;

        assertThatIllegalStateException().isThrownBy(() -> callerStore.release(callerPermit.permit()))
                .withMessage("offline");
        assertThat(executor.active("route")).isEqualTo(1);
        callerStore.release(callerPermit.permit());

        RedisCocoConcurrencyLimitStore closingStore = store(executor);
        closingStore.acquire(request(route(2)));
        executor.failNextReleases = 1;
        closingStore.close();
        assertThat(executor.active("route")).isOne();
        closingStore.close();

        assertThat(executor.active("route")).isZero();
        assertThat(executor.releaseAttempts).isEqualTo(4);
    }

    @Test
    void expiredOldTokenCannotReleaseNewOwner() {
        LeaseExecutor executor = new LeaseExecutor();
        RedisCocoConcurrencyLimitStore oldStore = store(executor);
        RedisCocoConcurrencyLimitStore newStore = store(executor);
        CocoConcurrencyLimitAcquisition old = oldStore.acquire(request(route(1)));
        executor.advance(Duration.ofSeconds(31));
        CocoConcurrencyLimitAcquisition current = newStore.acquire(request(route(1)));

        oldStore.release(old.permit());

        assertThat(executor.active("route")).isOne();
        assertThatIllegalArgumentException().isThrownBy(() -> newStore.release(old.permit()));
        assertThat(executor.active("route")).isOne();
        newStore.release(current.permit());
        assertThat(executor.active("route")).isZero();
    }

    @Test
    void rejectsInvalidResponsesAndExecutorFailuresWithoutLeakingData() {
        RedisConcurrencyLimitExecutor invalid = (operation, keys, arguments) -> "G:broken";
        assertThatIllegalStateException().isThrownBy(() -> store(invalid).acquire(request(route(1))))
                .satisfies(exception -> assertThat(exception.getMessage()).contains("invalid response"));
        RuntimeException failure = new IllegalStateException("offline");
        RedisConcurrencyLimitExecutor failing = (operation, keys, arguments) -> { throw failure; };
        assertThatIllegalStateException().isThrownBy(() -> store(failing).acquire(request(route(1)))).isSameAs(failure);
    }

    private static RedisCocoConcurrencyLimitStore store(RedisConcurrencyLimitExecutor executor) {
        CocoConcurrencyLimitRedisProperties properties = new CocoConcurrencyLimitRedisProperties();
        properties.setAppNamespace("orders");
        return new RedisCocoConcurrencyLimitStore(executor, properties, null, false);
    }
    private static CocoConcurrencyLimitRequest request(CocoConcurrencyLimitConstraint... constraints) { return new CocoConcurrencyLimitRequest(List.of(constraints)); }
    private static CocoConcurrencyLimitConstraint global(int limit) { return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.GLOBAL, "global", limit); }
    private static CocoConcurrencyLimitConstraint route(int limit) { return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.ROUTE, "orders", limit); }
    private record StoreAcquisition(RedisCocoConcurrencyLimitStore store, CocoConcurrencyLimitAcquisition acquisition) { }

    /** 线程安全的 Redis lease 协议状态机；时钟只由测试推进。 */
    private static final class LeaseExecutor implements RedisConcurrencyLimitExecutor {
        private final Map<String, PermitState> permits = new HashMap<>();
        private Instant now = Instant.parse("2026-08-14T00:00:00Z");
        private int failNextRenewals;
        private int failNextReleases;
        private int renewAttempts;
        private int releaseAttempts;
        @Override public synchronized String execute(RedisConcurrencyLimitOperation operation, List<String> keys, List<String> args) {
            cleanup();
            return switch (operation) { case ACQUIRE -> acquire(keys, args); case RENEW -> renew(args); case RELEASE -> release(args); };
        }
        private String acquire(List<String> keys, List<String> args) {
            String token = args.get(0); long lease = Long.parseLong(args.get(1)); int count = Integer.parseInt(args.get(2));
            List<Integer> limits = new ArrayList<>(); for (int i=0;i<count;i++) limits.add(Integer.parseInt(args.get(3+i)));
            List<String> dimensions = args.subList(3 + count, 3 + count + count);
            for (int i=0;i<count;i++) if (active(dimensions.get(i)) >= limits.get(i)) return reply("R", i, limits, dimensions);
            this.permits.put(token, new PermitState(List.copyOf(dimensions), this.now.plusMillis(lease)));
            return reply("G", -1, limits, dimensions);
        }
        private String renew(List<String> args) {
            this.renewAttempts++;
            if (this.failNextRenewals > 0) {
                this.failNextRenewals--;
                throw new IllegalStateException("offline");
            }
            PermitState permit = this.permits.get(args.get(0));
            if (permit == null) {
                return "0";
            }
            permit.expiresAt = this.now.plusMillis(Long.parseLong(args.get(1)));
            return "1";
        }
        private String release(List<String> args) {
            this.releaseAttempts++;
            if (this.failNextReleases > 0) {
                this.failNextReleases--;
                throw new IllegalStateException("offline");
            }
            return this.permits.remove(args.get(0)) == null ? "0" : "1";
        }
        private String reply(String type, int rejected, List<Integer> limits, List<String> dimensions) {
            StringBuilder result = new StringBuilder(type);
            if (rejected >= 0) {
                result.append(':').append(rejected + 1);
            }
            for (int index = 0; index < limits.size(); index++) {
                result.append(':').append(limits.get(index)).append(',')
                        .append(Math.max(0, limits.get(index) - active(dimensions.get(index))));
            }
            return result.toString();
        }
        private int active(String dimension) {
            cleanup();
            String key = "route".equals(dimension) ? digest(CocoConcurrencyLimitDimension.ROUTE, "orders")
                    : "global".equals(dimension) ? digest(CocoConcurrencyLimitDimension.GLOBAL, "global") : dimension;
            return (int) this.permits.values().stream().filter(value -> value.dimensions.contains(key)).count();
        }
        private void advance(Duration duration) { this.now=this.now.plus(duration); cleanup(); }
        private void cleanup() { this.permits.entrySet().removeIf(entry -> !entry.getValue().expiresAt.isAfter(this.now)); }
        private static String digest(CocoConcurrencyLimitDimension dimension, String key) {
            try {
                return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest((dimension.name() + '\0' + key).getBytes(StandardCharsets.UTF_8)));
            }
            catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
        private static final class PermitState { private final List<String> dimensions; private Instant expiresAt; private PermitState(List<String> dimensions, Instant expiresAt) { this.dimensions=dimensions; this.expiresAt=expiresAt; } }
    }
}
