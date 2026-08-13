package io.github.coco.feature.idempotency.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireStatus;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC 幂等存储 H2 集成测试。 */
class JdbcCocoIdempotencyStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-14T10:00:00Z");

    private HikariDataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    private JdbcCocoIdempotencyStore store;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:idempotency_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        config.setUsername("sa");
        config.setMaximumPoolSize(24);
        this.dataSource = new HikariDataSource(config);
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.jdbcTemplate.execute("""
                CREATE TABLE coco_idempotency (
                    idempotency_key VARCHAR(128) NOT NULL PRIMARY KEY,
                    request_hash VARCHAR(64) NOT NULL,
                    owner_token VARCHAR(64),
                    status VARCHAR(16) NOT NULL,
                    expires_at_epoch_millis BIGINT NOT NULL,
                    response_status INTEGER,
                    response_headers_json CLOB,
                    response_body BLOB
                )
                """);
        this.store = new JdbcCocoIdempotencyStore(this.dataSource, properties());
    }

    @AfterEach
    void tearDown() {
        if (this.dataSource != null) this.dataSource.close();
    }

    @Test
    void acquiresReplaysAndRejectsMismatchedPayloads() {
        CocoIdempotencyRequest request = request("key", "one");
        var acquired = this.store.acquire(request, NOW, NOW.plusSeconds(60));
        assertThat(acquired.status()).isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
        assertThat(acquired.lease().orElseThrow().ownerToken()).matches("[0-9a-f]{64}");
        assertThat(this.store.acquire(request, NOW, NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.IN_PROGRESS);
        assertThat(this.store.acquire(request("key", "two"), NOW, NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.PAYLOAD_MISMATCH);

        CocoIdempotencyStoredResponse response = new CocoIdempotencyStoredResponse(201,
                Map.of("X-Result", List.of("created"), "X-Binary", List.of("yes")), new byte[] { 0, 1, -1 });
        assertThat(this.store.complete(acquired.lease().orElseThrow(), response, NOW.plusSeconds(1))).isTrue();
        var replay = this.store.acquire(request, NOW.plusSeconds(2), NOW.plusSeconds(60));
        assertThat(replay.status()).isEqualTo(CocoIdempotencyAcquireStatus.REPLAY);
        assertThat(replay.response().orElseThrow().status()).isEqualTo(201);
        assertThat(replay.response().orElseThrow().headers()).containsEntry("X-Result", List.of("created"));
        assertThat(replay.response().orElseThrow().body()).containsExactly((byte) 0, (byte) 1, (byte) -1);
        assertThat(this.dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    @Test
    void failReleasesLeaseAndExpiredEntryCanBeTakenOver() {
        CocoIdempotencyRequest failed = request("failed", "payload");
        CocoIdempotencyLease failedLease = this.store.acquire(failed, NOW, NOW.plusSeconds(60)).lease().orElseThrow();
        assertThat(this.store.fail(failedLease, NOW.plusSeconds(1))).isTrue();
        assertThat(this.store.acquire(failed, NOW.plusSeconds(2), NOW.plusSeconds(62)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);

        CocoIdempotencyRequest expired = request("expired", "payload");
        this.store.acquire(expired, NOW, NOW.plusSeconds(1));
        assertThat(this.store.acquire(expired, NOW.plusSeconds(1), NOW.plusSeconds(61)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
    }

    @Test
    void staleAndForeignLeasesCannotCompleteOrFail() {
        CocoIdempotencyRequest request = request("owner", "payload");
        CocoIdempotencyLease lease = this.store.acquire(request, NOW, NOW.plusSeconds(60)).lease().orElseThrow();
        CocoIdempotencyLease foreign = new CocoIdempotencyLease(request, "f".repeat(64), lease.expiresAt());
        CocoIdempotencyLease stale = new CocoIdempotencyLease(request, lease.ownerToken(), NOW.plusSeconds(59));
        CocoIdempotencyStoredResponse response = new CocoIdempotencyStoredResponse(200, Map.of(), new byte[0]);

        assertThat(this.store.complete(foreign, response, NOW)).isFalse();
        assertThat(this.store.fail(foreign, NOW)).isFalse();
        assertThat(this.store.complete(stale, response, NOW)).isFalse();
        assertThat(this.store.fail(stale, NOW)).isFalse();
        assertThat(this.store.complete(lease, response, NOW)).isTrue();
    }

    @Test
    void concurrentAcquireGivesExactlyOneExecutionLease() throws Exception {
        CocoIdempotencyRequest request = request("concurrent", "payload");
        int contenders = 20;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<CocoIdempotencyAcquireStatus>> futures = new ArrayList<>();
            for (int index = 0; index < contenders; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return this.store.acquire(request, NOW, NOW.plusSeconds(60)).status();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CocoIdempotencyAcquireStatus> statuses = new ArrayList<>();
            for (Future<CocoIdempotencyAcquireStatus> future : futures) statuses.add(future.get(10, TimeUnit.SECONDS));
            assertThat(statuses).containsOnly(CocoIdempotencyAcquireStatus.ACQUIRED,
                    CocoIdempotencyAcquireStatus.IN_PROGRESS);
            assertThat(statuses).filteredOn(status -> status == CocoIdempotencyAcquireStatus.ACQUIRED).hasSize(1);
            assertThat(statuses).filteredOn(status -> status == CocoIdempotencyAcquireStatus.IN_PROGRESS)
                    .hasSize(contenders - 1);
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void commitsOwnStateOutsideCallingBusinessTransaction() {
        this.jdbcTemplate.execute("CREATE TABLE business_marker (marker_value VARCHAR(32) NOT NULL)");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(this.dataSource));
        CocoIdempotencyRequest request = request("independent", "payload");
        transaction.executeWithoutResult(status -> {
            this.jdbcTemplate.update("INSERT INTO business_marker(marker_value) VALUES ('rollback')");
            CocoIdempotencyLease lease = this.store.acquire(request, NOW, NOW.plusSeconds(60)).lease().orElseThrow();
            assertThat(this.store.complete(lease, new CocoIdempotencyStoredResponse(204, Map.of(), new byte[0]), NOW))
                    .isTrue();
            status.setRollbackOnly();
        });
        assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_marker", Integer.class)).isZero();
        assertThat(this.store.acquire(request, NOW.plusSeconds(1), NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.REPLAY);
    }

    @Test
    void rejectsOversizedOrCorruptResponsesWithoutCommittingPartialState() {
        CocoIdempotencyJdbcProperties limited = properties();
        limited.setMaxResponseBytes(2);
        JdbcCocoIdempotencyStore limitedStore = new JdbcCocoIdempotencyStore(this.dataSource, limited);
        CocoIdempotencyRequest limitedRequest = request("limited", "payload");
        CocoIdempotencyLease lease = limitedStore.acquire(limitedRequest, NOW, NOW.plusSeconds(60)).lease().orElseThrow();
        assertThatThrownBy(() -> limitedStore.complete(lease,
                new CocoIdempotencyStoredResponse(200, Map.of(), new byte[] { 1, 2, 3 }), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max-response-bytes");
        assertThat(limitedStore.acquire(limitedRequest, NOW, NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.IN_PROGRESS);

        CocoIdempotencyJdbcProperties headerLimited = properties();
        headerLimited.setMaxHeaderBytes(2);
        JdbcCocoIdempotencyStore headerLimitedStore = new JdbcCocoIdempotencyStore(this.dataSource, headerLimited);
        CocoIdempotencyLease headerLease = headerLimitedStore.acquire(request("headers", "payload"), NOW,
                NOW.plusSeconds(60)).lease().orElseThrow();
        assertThatThrownBy(() -> headerLimitedStore.complete(headerLease,
                new CocoIdempotencyStoredResponse(200, Map.of("X-Result", List.of("created")), new byte[0]), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("max-header-bytes");

        CocoIdempotencyLease statusLease = this.store.acquire(request("status", "payload"), NOW,
                NOW.plusSeconds(60)).lease().orElseThrow();
        assertThatThrownBy(() -> this.store.complete(statusLease,
                new CocoIdempotencyStoredResponse(600, Map.of(), new byte[0]), NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100 and 599");

        this.jdbcTemplate.update("INSERT INTO coco_idempotency (idempotency_key, request_hash, owner_token, status, "
                        + "expires_at_epoch_millis, response_status, response_headers_json, response_body) "
                        + "VALUES (?, ?, ?, 'COMPLETED', ?, 200, ?, ?)",
                "coco:" + hash("corrupt"), hash("payload"), "token", NOW.plusSeconds(60).toEpochMilli(),
                "[\"not-an-object\"]", new byte[] { 1 });
        assertThatThrownBy(() -> this.store.acquire(request("corrupt", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("headers are invalid");
    }

    @Test
    void rejectsUnsafePropertiesPropagatesConnectionErrorsAndDoesNotCloseDataSource() throws Exception {
        for (String identifier : List.of("bad.table", "bad;drop", "bad name", "1bad", "\"bad\"")) {
            CocoIdempotencyJdbcProperties properties = properties();
            properties.setTableName(identifier);
            assertThatThrownBy(() -> new JdbcCocoIdempotencyStore(this.dataSource, properties))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        CocoIdempotencyJdbcProperties prefixProperties = properties();
        prefixProperties.setKeyPrefix("coco: bad");
        assertThatThrownBy(() -> new JdbcCocoIdempotencyStore(this.dataSource, prefixProperties))
                .isInstanceOf(IllegalArgumentException.class);

        DataSource failing = new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException { throw new java.sql.SQLException("down", "08001"); }
            @Override public Connection getConnection(String username, String password) throws java.sql.SQLException { return getConnection(); }
            @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
        };
        assertThatThrownBy(() -> new JdbcCocoIdempotencyStore(failing, properties())
                .acquire(request("down", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(UncategorizedSQLException.class).hasCauseInstanceOf(java.sql.SQLException.class);

        this.store.close();
        assertThat(this.dataSource.isClosed()).isFalse();
        assertThatThrownBy(() -> this.store.acquire(request("closed", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
        assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coco_idempotency", Integer.class)).isZero();
    }

    private static CocoIdempotencyJdbcProperties properties() {
        CocoIdempotencyJdbcProperties properties = new CocoIdempotencyJdbcProperties();
        properties.setKeyPrefix("coco:");
        return properties;
    }

    private static CocoIdempotencyRequest request(String key, String payload) {
        return new CocoIdempotencyRequest(hash(key), hash(payload));
    }

    private static String hash(String value) {
        return String.format("%064x", new java.math.BigInteger(1, value.getBytes(StandardCharsets.UTF_8)));
    }
}
