package io.github.coco.feature.web.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcCocoReplayStoreTest {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private JdbcTemplate jdbcTemplate;

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.execute("""
                CREATE TABLE coco_replay_key (
                    replay_key_hash VARCHAR(64) NOT NULL,
                    expires_at_epoch_millis BIGINT NOT NULL,
                    PRIMARY KEY (replay_key_hash)
                )
                """);
        this.jdbcTemplate.execute("""
                CREATE INDEX idx_coco_replay_key_expires_at
                    ON coco_replay_key (expires_at_epoch_millis)
                """);
        this.clock = new MutableClock(BASE_TIME);
    }

    @Test
    void firstReservationSucceedsAndActiveDuplicateIsRejected() {
        JdbcCocoReplayStore store = newStore();
        CocoReplayKey replayKey = key("same");

        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(60)));
        assertFalse(store.reserve(replayKey, BASE_TIME.plusSeconds(120)));

        assertEquals(1, rowCount());
        assertEquals(BASE_TIME.plusSeconds(60).toEpochMilli(), storedExpiration());
    }

    @Test
    void expiredReservationIsAtomicallyUpdated() {
        JdbcCocoReplayStore store = newStore();
        CocoReplayKey replayKey = key("expired");
        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(1)));
        this.clock.set(BASE_TIME.plusSeconds(2));

        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(60)));

        assertEquals(1, rowCount());
        assertEquals(BASE_TIME.plusSeconds(60).toEpochMilli(), storedExpiration());
    }

    @Test
    void concurrentFirstReservationsHaveSingleWinner() throws Exception {
        assertSingleConcurrentWinner(newStore(), key("concurrent-first"), BASE_TIME.plusSeconds(60));
    }

    @Test
    void concurrentExpiredReservationsHaveSingleWinner() throws Exception {
        JdbcCocoReplayStore store = newStore();
        CocoReplayKey replayKey = key("concurrent-expired");
        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(1)));
        this.clock.set(BASE_TIME.plusSeconds(2));

        assertSingleConcurrentWinner(store, replayKey, BASE_TIME.plusSeconds(60));
        assertEquals(1, rowCount());
    }

    @Test
    void cleanupDeletesOnlyExpiredReservations() {
        JdbcCocoReplayStore store = newStore();
        assertTrue(store.reserve(key("expired"), BASE_TIME.plusSeconds(1)));
        assertTrue(store.reserve(key("active"), BASE_TIME.plusSeconds(60)));
        this.clock.set(BASE_TIME.plusSeconds(2));

        assertEquals(1, store.cleanupExpiredKeys());
        assertEquals(1, rowCount());
        assertEquals(BASE_TIME.plusSeconds(60).toEpochMilli(), storedExpiration());
    }

    @Test
    void cleanupStartsLazilyOnFirstReservation() {
        CocoReplayProperties properties = properties();
        JdbcCocoReplayStore store = new JdbcCocoReplayStore(this.jdbcTemplate, properties, this.clock, true);
        try {
            assertFalse(store.cleanupStarted());

            assertTrue(store.reserve(key("lazy-cleanup"), BASE_TIME.plusSeconds(60)));

            assertTrue(store.cleanupStarted());
        }
        finally {
            store.close();
        }
    }

    @Test
    void storesOnlyLowercaseSha256Digest() throws Exception {
        JdbcCocoReplayStore store = newStore();
        CocoReplayKey replayKey = key("sensitive-nonce");

        assertTrue(store.reserve(replayKey, BASE_TIME.plusSeconds(60)));

        String storedHash = this.jdbcTemplate.queryForObject(
                "SELECT replay_key_hash FROM coco_replay_key", String.class);
        assertEquals(sha256(replayKey.value()), storedHash);
        assertTrue(storedHash.matches("[0-9a-f]{64}"));
        assertNotEquals(replayKey.value(), storedHash);
        assertFalse(storedHash.contains("sensitive-nonce"));
    }

    @Test
    void rejectsUnsafeOrDeeplyQualifiedTableNames() {
        List<String> invalidNames = List.of(
                "coco replay", "coco_replay_key;DROP_TABLE", "schema.table.extra",
                "schema..table", "quoted-table", "coco_replay_key -- comment");

        for (String invalidName : invalidNames) {
            CocoReplayProperties properties = properties();
            properties.getJdbc().setTableName(invalidName);
            assertThrows(IllegalArgumentException.class,
                    () -> new JdbcCocoReplayStore(this.jdbcTemplate, properties, this.clock, false));
        }
    }

    @Test
    void acceptsSingleSchemaQualifiedTableName() {
        this.jdbcTemplate.execute("CREATE SCHEMA replay_schema");
        this.jdbcTemplate.execute("""
                CREATE TABLE replay_schema.coco_replay_key (
                    replay_key_hash VARCHAR(64) NOT NULL PRIMARY KEY,
                    expires_at_epoch_millis BIGINT NOT NULL
                )
                """);
        CocoReplayProperties properties = properties();
        properties.getJdbc().setTableName("replay_schema.coco_replay_key");
        JdbcCocoReplayStore store = new JdbcCocoReplayStore(
                this.jdbcTemplate, properties, this.clock, false);

        assertTrue(store.reserve(key("schema"), BASE_TIME.plusSeconds(60)));
    }

    @Test
    void databaseFailurePropagatesFromReservation() {
        JdbcCocoReplayStore store = newStore();
        this.jdbcTemplate.execute("DROP TABLE coco_replay_key");

        assertThrows(DataAccessException.class,
                () -> store.reserve(key("database-down"), BASE_TIME.plusSeconds(60)));
    }

    @Test
    void failedReservationDoesNotStartBackgroundCleanup() {
        JdbcCocoReplayStore store = new JdbcCocoReplayStore(
                this.jdbcTemplate, properties(), this.clock, true);
        try {
            this.jdbcTemplate.execute("DROP TABLE coco_replay_key");

            assertThrows(DataAccessException.class,
                    () -> store.reserve(key("database-down-cleanup"), BASE_TIME.plusSeconds(60)));

            assertFalse(store.cleanupStarted());
        }
        finally {
            store.close();
        }
    }

    @Test
    void nonUniqueInsertFailurePropagatesFromReservation() {
        this.jdbcTemplate.execute("""
                ALTER TABLE coco_replay_key ADD CONSTRAINT expires_at_non_negative
                    CHECK (expires_at_epoch_millis >= 0)
                """);
        JdbcCocoReplayStore store = newStore();

        assertThrows(DataAccessException.class,
                () -> store.reserve(key("invalid-expiration"), Instant.ofEpochMilli(-1)));
    }

    private JdbcCocoReplayStore newStore() {
        return new JdbcCocoReplayStore(this.jdbcTemplate, properties(), this.clock, false);
    }

    private static CocoReplayProperties properties() {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setCleanupIntervalSeconds(1);
        return properties;
    }

    private int rowCount() {
        Integer count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coco_replay_key", Integer.class);
        return count == null ? 0 : count;
    }

    private long storedExpiration() {
        Long expiresAt = this.jdbcTemplate.queryForObject(
                "SELECT expires_at_epoch_millis FROM coco_replay_key", Long.class);
        return expiresAt == null ? 0L : expiresAt;
    }

    private static void assertSingleConcurrentWinner(JdbcCocoReplayStore store, CocoReplayKey replayKey,
            Instant expiresAt) throws Exception {
        int contenders = 12;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, contenders)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        return store.reserve(replayKey, expiresAt);
                    }))
                    .toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertEquals(1, winners);
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static CocoReplayKey key(String nonce) {
        return new CocoReplayKey("app-1", "key-1", BASE_TIME.toString(), nonce, "POST", "/api/orders");
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static final class MutableClock extends Clock {

        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.instant;
        }
    }
}
