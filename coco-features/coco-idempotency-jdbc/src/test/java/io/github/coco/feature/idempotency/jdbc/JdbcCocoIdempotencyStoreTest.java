package io.github.coco.feature.idempotency.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
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
    void rejectsTransactionAwareProxyWithoutCommittingBusinessTransaction() {
        this.jdbcTemplate.execute("CREATE TABLE proxy_marker (marker_value VARCHAR(32) NOT NULL)");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(this.dataSource));
        transaction.executeWithoutResult(status -> {
            this.jdbcTemplate.update("INSERT INTO proxy_marker(marker_value) VALUES ('rollback')");
            assertThatThrownBy(() -> new JdbcCocoIdempotencyStore(
                    new TransactionAwareDataSourceProxy(this.dataSource), properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be transaction-aware");
            assertThatThrownBy(() -> new JdbcCocoIdempotencyStore(
                    new LazyConnectionDataSourceProxy(new TransactionAwareDataSourceProxy(this.dataSource)),
                    properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be transaction-aware");
            assertThatThrownBy(() -> new JdbcCocoIdempotencyStore(
                    unwrapOnlyProxy(new TransactionAwareDataSourceProxy(this.dataSource)), properties()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be transaction-aware");
            status.setRollbackOnly();
        });
        assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM proxy_marker", Integer.class)).isZero();
    }

    @Test
    void integrityConstraintFailuresWithoutACompetingRowAreRethrown() {
        this.jdbcTemplate.execute("ALTER TABLE coco_idempotency ADD CONSTRAINT reject_key "
                + "CHECK (idempotency_key = 'never')");
        assertConstraintFailure(request("check", "payload"), "23513", 0);
    }

    @Test
    void notNullAndForeignKeyFailuresWithoutACompetingRowAreRethrown() {
        this.jdbcTemplate.execute("ALTER TABLE coco_idempotency ADD required_marker VARCHAR(1) NOT NULL");
        assertConstraintFailure(request("not-null", "payload"), "23502", 0);

        this.jdbcTemplate.execute("ALTER TABLE coco_idempotency DROP COLUMN required_marker");
        this.jdbcTemplate.execute("CREATE TABLE idempotency_parent (parent_id INTEGER PRIMARY KEY)");
        this.jdbcTemplate.execute("ALTER TABLE coco_idempotency ADD parent_id INTEGER DEFAULT 999 NOT NULL");
        this.jdbcTemplate.execute("ALTER TABLE coco_idempotency ADD CONSTRAINT fk_idempotency_parent "
                + "FOREIGN KEY (parent_id) REFERENCES idempotency_parent(parent_id)");
        assertConstraintFailure(request("foreign-key", "payload"), "23506", 0);
    }

    @Test
    void checkViolationIsNotTreatedAsDuplicateWhenTheKeyAlreadyExists() {
        CocoIdempotencyRequest existing = request("same-key-check", "accepted");
        assertThat(this.store.acquire(existing, NOW, NOW.plusSeconds(60)).status())
                .isEqualTo(CocoIdempotencyAcquireStatus.ACQUIRED);
        this.jdbcTemplate.execute("ALTER TABLE coco_idempotency ADD CONSTRAINT reject_other_payloads CHECK "
                + "(request_hash = '" + existing.requestHash() + "')");

        assertConstraintFailure(request("same-key-check", "rejected"), "23513", 1);
    }

    @Test
    void recognizesOnlyExplicitPostgresqlH2AndMysqlDuplicateCodesAcrossExceptionChains() throws Throwable {
        Method duplicateKey = JdbcCocoIdempotencyStore.class.getDeclaredMethod("isDuplicateKey", SQLException.class);
        duplicateKey.setAccessible(true);

        assertThat(invoke(duplicateKey, null, new SQLException("duplicate", "23505"))).isEqualTo(true);
        assertThat(invoke(duplicateKey, null, new SQLException("duplicate", "23000", 1062))).isEqualTo(true);

        SQLException nextException = new SQLException("wrapper", "HY000");
        nextException.setNextException(new SQLException("duplicate", "23000", 1062));
        assertThat(invoke(duplicateKey, null, nextException)).isEqualTo(true);
        SQLException causeException = new SQLException("wrapper", "HY000",
                new SQLException("duplicate", "23505"));
        assertThat(invoke(duplicateKey, null, causeException)).isEqualTo(true);

        for (SQLException notDuplicate : List.of(new SQLException("check", "23513"),
                new SQLException("not-null", "23502"), new SQLException("foreign-key", "23503"),
                new SQLException("unknown mysql", "23000", 9999), new SQLException("unknown", "ZZZZZ"))) {
            assertThat(invoke(duplicateKey, null, notDuplicate)).isEqualTo(false);
        }
    }

    @Test
    void unknownInsertSqlStatePropagatesTheOriginalException() {
        SQLException original = new SQLException("unknown insert failure", "ZZZZZ", 777);
        JdbcCocoIdempotencyStore unknownDriverStore = new JdbcCocoIdempotencyStore(
                insertFailingDataSource(this.dataSource, original), properties());

        assertThatThrownBy(() -> unknownDriverStore.acquire(request("unknown-driver", "payload"), NOW,
                NOW.plusSeconds(60)))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .satisfies(failure -> assertThat(failure.getCause()).isSameAs(original));
    }

    @Test
    void rejectsOversizedOrCorruptResponsesWithoutCommittingPartialState() {
        CocoIdempotencyJdbcProperties limited = properties();
        limited.setMaxResponseBytes(2);
        JdbcCocoIdempotencyStore limitedStore = new JdbcCocoIdempotencyStore(this.dataSource, limited);
        CocoIdempotencyRequest limitedRequest = request("limited", "payload");
        CocoIdempotencyLease lease = limitedStore.acquire(limitedRequest, NOW, NOW.plusSeconds(60))
                .lease().orElseThrow();
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
    void boundsStoredLobsByUtf8BytesWithoutExposingTheirContents() {
        CocoIdempotencyJdbcProperties limited = properties();
        limited.setMaxHeaderBytes(32);
        limited.setMaxResponseBytes(16);
        JdbcCocoIdempotencyStore limitedStore = new JdbcCocoIdempotencyStore(this.dataSource, limited);

        String headerMaxPlusOne = "{\"X\":[\"" + "h".repeat(23) + "\"]}";
        assertThat(headerMaxPlusOne.getBytes(StandardCharsets.UTF_8)).hasSize(limited.getMaxHeaderBytes() + 1);
        insertCompleted("header-max-plus-one", "payload", headerMaxPlusOne, new byte[0]);
        assertThatThrownBy(() -> limitedStore.acquire(request("header-max-plus-one", "payload"), NOW,
                NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("headers exceed configured limit");

        String headerSecret = "header-secret-" + "汉".repeat(40);
        insertCompleted("much-larger-header", "payload", "{\"X\":[\"" + headerSecret + "\"]}", new byte[0]);
        assertThatThrownBy(() -> limitedStore.acquire(request("much-larger-header", "payload"), NOW,
                NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("headers exceed configured limit")
                .hasMessageNotContaining(headerSecret);

        byte[] bodyMaxPlusOne = "body-secret-value".getBytes(StandardCharsets.UTF_8);
        assertThat(bodyMaxPlusOne).hasSize(limited.getMaxResponseBytes() + 1);
        insertCompleted("body-max-plus-one", "payload", "{}", bodyMaxPlusOne);
        assertThatThrownBy(() -> limitedStore.acquire(request("body-max-plus-one", "payload"), NOW,
                NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("body exceeds configured limit")
                .hasMessageNotContaining("body-secret-value");

        byte[] muchLargerBody = new byte[100_000];
        java.util.Arrays.fill(muchLargerBody, (byte) 0x5a);
        insertCompleted("much-larger-body", "payload", "{}", muchLargerBody);
        assertThatThrownBy(() -> limitedStore.acquire(request("much-larger-body", "payload"), NOW,
                NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("body exceeds configured limit");
    }

    @Test
    void delegatesStoredHeaderValidationToStoredResponse() {
        insertCompleted("invalid-header", "payload", "{\"\":[\"hidden-value\"]}", new byte[0]);
        assertThatThrownBy(() -> this.store.acquire(request("invalid-header", "payload"), NOW, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("headers are invalid")
                .hasMessageNotContaining("hidden-value")
                .hasRootCauseMessage("header name must not be blank");
    }

    @Test
    void preservesLobCloseFailuresAsSuppressedOnLimitErrors() throws Exception {
        CocoIdempotencyJdbcProperties limited = properties();
        limited.setMaxHeaderBytes(1);
        limited.setMaxResponseBytes(1);
        JdbcCocoIdempotencyStore limitedStore = new JdbcCocoIdempotencyStore(this.dataSource, limited);
        IOException headerCloseFailure = new IOException("header stream close failed");
        IOException bodyCloseFailure = new IOException("body stream close failed");
        Method readHeaders = JdbcCocoIdempotencyStore.class.getDeclaredMethod("readHeaders", java.io.Reader.class);
        Method readBody = JdbcCocoIdempotencyStore.class.getDeclaredMethod("readBody", java.io.InputStream.class);
        readHeaders.setAccessible(true);
        readBody.setAccessible(true);

        Reader failingReader = new Reader() {
            private final StringReader delegate = new StringReader("xx");
            @Override public int read(char[] buffer, int offset, int length) throws IOException {
                return this.delegate.read(buffer, offset, length);
            }
            @Override public int read() throws IOException { return this.delegate.read(); }
            @Override public void close() throws IOException { throw headerCloseFailure; }
        };
        assertThatThrownBy(() -> invoke(readHeaders, limitedStore, failingReader))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("headers exceed configured limit")
                .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(headerCloseFailure));
        assertThatThrownBy(() -> invoke(readBody, limitedStore, new ByteArrayInputStream(new byte[] { 1, 2 }) {
            @Override public void close() throws IOException { throw bodyCloseFailure; }
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("body exceeds configured limit")
                .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(bodyCloseFailure));
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
            @Override public Connection getConnection() throws SQLException {
                throw new SQLException("down", "08001");
            }
            @Override public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
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
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessage("JDBC idempotency database operation failed")
                .hasCauseInstanceOf(SQLException.class);

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

    private void assertConstraintFailure(CocoIdempotencyRequest request, String expectedSqlState,
            int expectedRowCount) {
        assertThatThrownBy(() -> this.store.acquire(request, NOW, NOW.plusSeconds(60)))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .satisfies(failure -> {
                    SQLException sqlException = (SQLException) failure.getCause();
                    assertThat(sqlException.getSQLState()).isEqualTo(expectedSqlState);
                    assertThat(failure.getMessage()).doesNotContain(request.keyHash(), request.requestHash());
                });
        assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coco_idempotency", Integer.class))
                .isEqualTo(expectedRowCount);
    }

    private void insertCompleted(String key, String payload, String headersJson, byte[] body) {
        this.jdbcTemplate.update("INSERT INTO coco_idempotency (idempotency_key, request_hash, owner_token, status, "
                        + "expires_at_epoch_millis, response_status, response_headers_json, response_body) "
                        + "VALUES (?, ?, ?, 'COMPLETED', ?, 200, ?, ?)",
                "coco:" + hash(key), hash(payload), "stored-owner", NOW.plusSeconds(60).toEpochMilli(),
                headersJson, body);
    }

    private static DataSource unwrapOnlyProxy(TransactionAwareDataSourceProxy transactionAwareDataSource) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "isWrapperFor" -> arguments[0] == TransactionAwareDataSourceProxy.class;
                    case "unwrap" -> arguments[0] == TransactionAwareDataSourceProxy.class
                            ? transactionAwareDataSource : null;
                    case "toString" -> "unwrapOnlyDataSource";
                    default -> throw new AssertionError("Unexpected DataSource method: " + method.getName());
                });
    }

    private static DataSource insertFailingDataSource(DataSource target, SQLException failure) {
        return new DelegatingDataSource(target) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[] { Connection.class }, (proxy, method, arguments) -> {
                            if ("prepareStatement".equals(method.getName()) && arguments != null
                                    && arguments.length > 0 && arguments[0] instanceof String sql
                                    && sql.startsWith("INSERT INTO")) {
                                throw failure;
                            }
                            try {
                                return method.invoke(connection, arguments);
                            }
                            catch (InvocationTargetException ex) {
                                throw ex.getCause();
                            }
                        });
            }
        };
    }

    private static Object invoke(Method method, Object target, Object argument) throws Throwable {
        try {
            return method.invoke(target, argument);
        }
        catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private static CocoIdempotencyRequest request(String key, String payload) {
        return new CocoIdempotencyRequest(hash(key), hash(payload));
    }

    private static String hash(String value) {
        return String.format("%064x", new java.math.BigInteger(1, value.getBytes(StandardCharsets.UTF_8)));
    }
}
