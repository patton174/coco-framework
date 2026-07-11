package io.github.coco.web.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 基于 JDBC 的 Coco Web 防重放共享存储。
 * <p>
 * 使用业务项目提供的 {@link JdbcOperations} 和预建表，通过防重放键摘要唯一约束实现跨实例原子占用。
 * 框架不会创建表、管理数据源或选择事务管理器。自动配置场景由过滤器在业务事务开始前调用；
 * 业务直接调用时，{@link JdbcOperations} 仍会遵循调用线程已有事务。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class JdbcCocoReplayStore implements CocoReplayStore, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcCocoReplayStore.class);

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final JdbcOperations jdbcOperations;

    private final long cleanupIntervalSeconds;

    private final Clock clock;

    private final String insertSql;

    private final String reserveExpiredKeySql;

    private final String cleanupSql;

    private final ScheduledExecutorService cleanupExecutor;

    private final AtomicBoolean cleanupStarted = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * <p>
     * 创建 JDBC 防重放共享存储。
     * </p>
     * @param jdbcOperations 业务项目提供的 JDBC 操作入口
     * @param properties 防重放配置属性
     */
    public JdbcCocoReplayStore(JdbcOperations jdbcOperations, CocoReplayProperties properties) {
        this(jdbcOperations, properties, Clock.systemUTC(), true);
    }

    JdbcCocoReplayStore(JdbcOperations jdbcOperations, CocoReplayProperties properties, Clock clock,
            boolean backgroundCleanupEnabled) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        CocoReplayProperties replayProperties = properties == null ? new CocoReplayProperties() : properties;
        this.cleanupIntervalSeconds = replayProperties.getCleanupIntervalSeconds();
        this.clock = clock == null ? Clock.systemUTC() : clock;
        String tableName = validateTableName(replayProperties.getJdbc().getTableName());
        this.insertSql = "INSERT INTO " + tableName
                + " (replay_key_hash, expires_at_epoch_millis) VALUES (?, ?)";
        this.reserveExpiredKeySql = "UPDATE " + tableName
                + " SET expires_at_epoch_millis = ?"
                + " WHERE replay_key_hash = ? AND expires_at_epoch_millis <= ?";
        this.cleanupSql = "DELETE FROM " + tableName + " WHERE expires_at_epoch_millis <= ?";
        this.cleanupExecutor = backgroundCleanupEnabled
                ? Executors.newSingleThreadScheduledExecutor(new CleanupThreadFactory())
                : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean reserve(CocoReplayKey key, Instant expiresAt) {
        CocoReplayKey checkedKey = Objects.requireNonNull(key, "key must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        String replayKeyHash = sha256(checkedKey.value());
        long nowEpochMillis = this.clock.millis();
        long expiresAtEpochMillis = checkedExpiresAt.toEpochMilli();
        int updated = this.jdbcOperations.update(this.reserveExpiredKeySql,
                expiresAtEpochMillis, replayKeyHash, nowEpochMillis);
        boolean reserved;
        if (updated == 1) {
            reserved = true;
        }
        else if (updated != 0) {
            throw new IllegalStateException("Coco replay key uniqueness contract was violated");
        }
        else {
            try {
                insert(replayKeyHash, expiresAtEpochMillis);
                reserved = true;
            }
            catch (DuplicateKeyException ex) {
                reserved = false;
            }
        }
        startCleanupTaskIfNecessary();
        return reserved;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (this.cleanupExecutor != null && this.closed.compareAndSet(false, true)) {
            this.cleanupExecutor.shutdownNow();
        }
    }

    int cleanupExpiredKeys() {
        return this.jdbcOperations.update(this.cleanupSql, this.clock.millis());
    }

    boolean cleanupStarted() {
        return this.cleanupStarted.get();
    }

    private void insert(String replayKeyHash, long expiresAtEpochMillis) {
        int inserted = this.jdbcOperations.update(this.insertSql, replayKeyHash, expiresAtEpochMillis);
        if (inserted != 1) {
            throw new IllegalStateException("Coco replay reservation insert affected " + inserted + " rows");
        }
    }

    private void startCleanupTaskIfNecessary() {
        if (this.cleanupExecutor == null || this.closed.get()
                || !this.cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupExpiredKeysSafely,
                this.cleanupIntervalSeconds, this.cleanupIntervalSeconds, TimeUnit.SECONDS);
    }

    private void cleanupExpiredKeysSafely() {
        try {
            cleanupExpiredKeys();
        }
        catch (RuntimeException ex) {
            LOGGER.warn("Coco JDBC replay cleanup failed; expired replay keys will be retried later.", ex);
        }
    }

    private static String validateTableName(String tableName) {
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("replay JDBC table name must be a simple or schema-qualified "
                    + "SQL identifier");
        }
        return tableName;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static final class CleanupThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "coco-replay-jdbc-cleanup");
            thread.setDaemon(true);
            return thread;
        }
    }
}
