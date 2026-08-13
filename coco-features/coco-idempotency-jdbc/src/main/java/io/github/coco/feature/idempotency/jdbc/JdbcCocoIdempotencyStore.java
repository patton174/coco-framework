package io.github.coco.feature.idempotency.jdbc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

/**
 * 基于 JDBC 的多实例幂等共享存储。
 * <p>每次操作都直接从 {@link DataSource} 获取独立连接，并只提交本存储的短事务；实现不会通过
 * Spring 的线程绑定连接工具取得连接，因此不会提交或回滚业务事务。业务数据源必须能够提供绝对独立的物理连接；
 * 可以使用普通业务连接池，也可以提供命名为 {@code cocoIdempotencyDataSource} 的专用连接池，但不能传入
 * {@link TransactionAwareDataSourceProxy} 或包装该代理的数据源。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class JdbcCocoIdempotencyStore implements CocoIdempotencyStore {

    private static final int MAX_ACQUIRE_ATTEMPTS = 6;

    private static final int MAX_SQL_EXCEPTION_NODES = 32;

    private static final SecureRandom OWNER_RANDOM = new SecureRandom();

    private final DataSource dataSource;

    private final CocoIdempotencyJdbcProperties properties;

    private final ObjectMapper objectMapper;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final String insertSql;

    private final String selectForUpdateSql;

    private final String deleteExpiredSql;

    private final String completeSql;

    private final String failSql;

    /**
     * 创建 JDBC 幂等存储。
     * @param dataSource 业务提供的数据源；不会由本类关闭
     * @param properties JDBC 适配器配置
     */
    public JdbcCocoIdempotencyStore(DataSource dataSource, CocoIdempotencyJdbcProperties properties) {
        this(dataSource, properties, new ObjectMapper());
    }

    /**
     * 创建 JDBC 幂等存储并指定 JSON 编解码器。
     * @param dataSource 业务提供的数据源；不会由本类关闭
     * @param properties JDBC 适配器配置
     * @param objectMapper JSON 编解码器
     */
    public JdbcCocoIdempotencyStore(DataSource dataSource, CocoIdempotencyJdbcProperties properties,
            ObjectMapper objectMapper) {
        this.dataSource = requireIndependentDataSource(dataSource);
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.properties.validate();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        String table = this.properties.qualifiedTableName();
        this.insertSql = "INSERT INTO " + table + " (idempotency_key, request_hash, owner_token, status, "
                + "expires_at_epoch_millis) VALUES (?, ?, ?, 'IN_PROGRESS', ?)";
        this.selectForUpdateSql = "SELECT request_hash, owner_token, status, expires_at_epoch_millis, response_status, "
                + "response_headers_json, response_body FROM " + table + " WHERE idempotency_key = ? FOR UPDATE";
        this.deleteExpiredSql = "DELETE FROM " + table + " WHERE idempotency_key = ? AND expires_at_epoch_millis = ? "
                + "AND status = ?";
        this.completeSql = "UPDATE " + table + " SET status = 'COMPLETED', response_status = ?, "
                + "response_headers_json = ?, response_body = ? WHERE idempotency_key = ? AND request_hash = ? "
                + "AND owner_token = ? AND status = 'IN_PROGRESS' AND expires_at_epoch_millis = ? "
                + "AND expires_at_epoch_millis > ?";
        this.failSql = "DELETE FROM " + table + " WHERE idempotency_key = ? AND request_hash = ? "
                + "AND owner_token = ? AND status = 'IN_PROGRESS' AND expires_at_epoch_millis = ? "
                + "AND expires_at_epoch_millis > ?";
    }

    /** {@inheritDoc} */
    @Override
    public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) {
        CocoIdempotencyRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!checkedExpiresAt.isAfter(checkedNow)) {
            throw new IllegalArgumentException("expiresAt must be after now");
        }
        ensureOpen();
        String ownerToken = ownerToken();
        for (int attempt = 0; attempt < MAX_ACQUIRE_ATTEMPTS; attempt++) {
            InsertAttempt insertion = tryInsert(checkedRequest, checkedExpiresAt, ownerToken);
            if (insertion.acquired) {
                return CocoIdempotencyAcquireResult.acquired(
                        new CocoIdempotencyLease(checkedRequest, ownerToken, checkedExpiresAt));
            }
            AcquireAttempt outcome;
            AtomicBoolean rowObserved = new AtomicBoolean();
            try {
                outcome = inTransaction(connection -> readOrRemoveExpired(connection, checkedRequest,
                        checkedNow, insertion.failure, rowObserved));
            }
            catch (RuntimeException verificationFailure) {
                if (!rowObserved.get() && verificationFailure != insertion.failure) {
                    insertion.failure.addSuppressed(verificationFailure);
                    throw insertion.failure;
                }
                throw verificationFailure;
            }
            if (outcome.result != null) return outcome.result;
        }
        throw new IllegalStateException("JDBC idempotency acquire could not establish a stable row state");
    }

    /** {@inheritDoc} */
    @Override
    public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) {
        CocoIdempotencyLease checkedLease = Objects.requireNonNull(lease, "lease must not be null");
        CocoIdempotencyStoredResponse checkedResponse = Objects.requireNonNull(response, "response must not be null");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        ensureOpen();
        StoredResponse stored = serializeResponse(checkedResponse);
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(this.completeSql)) {
                statement.setInt(1, stored.status);
                statement.setString(2, stored.headersJson);
                statement.setBytes(3, stored.body);
                bindLease(statement, 4, checkedLease, checkedNow);
                return statement.executeUpdate() == 1;
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean fail(CocoIdempotencyLease lease, Instant now) {
        CocoIdempotencyLease checkedLease = Objects.requireNonNull(lease, "lease must not be null");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        ensureOpen();
        return inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(this.failSql)) {
                bindLease(statement, 1, checkedLease, checkedNow);
                return statement.executeUpdate() == 1;
            }
        });
    }

    /** 关闭存储；不会关闭业务数据源。 */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private InsertAttempt tryInsert(CocoIdempotencyRequest request, Instant expiresAt, String ownerToken) {
        try {
            boolean inserted = inTransaction(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(this.insertSql)) {
                    statement.setString(1, storageKey(request));
                    statement.setString(2, request.requestHash());
                    statement.setString(3, ownerToken);
                    statement.setLong(4, expiresAt.toEpochMilli());
                    return statement.executeUpdate() == 1;
                }
            });
            if (!inserted) {
                throw new IllegalStateException("JDBC idempotency insert did not create a row");
            }
            return InsertAttempt.success();
        }
        catch (DataAccessResourceFailureException ex) {
            if (isDuplicateKey(sqlCause(ex))) return InsertAttempt.failed(ex);
            throw ex;
        }
    }

    private AcquireAttempt readOrRemoveExpired(Connection connection, CocoIdempotencyRequest request, Instant now,
            DataAccessResourceFailureException insertFailure, AtomicBoolean rowObserved) throws SQLException {
        Row row = selectForUpdate(connection, storageKey(request), request.requestHash(), rowObserved);
        if (row == null) throw insertFailure;
        if (row.expiresAtEpochMillis <= now.toEpochMilli()) {
            try (PreparedStatement statement = connection.prepareStatement(this.deleteExpiredSql)) {
                statement.setString(1, storageKey(request));
                statement.setLong(2, row.expiresAtEpochMillis);
                statement.setString(3, row.status);
                statement.executeUpdate();
            }
            return AcquireAttempt.retry();
        }
        if (!row.requestHash.equals(request.requestHash())) {
            return AcquireAttempt.result(CocoIdempotencyAcquireResult.payloadMismatch());
        }
        if ("IN_PROGRESS".equals(row.status)) {
            return AcquireAttempt.result(CocoIdempotencyAcquireResult.inProgress());
        }
        if ("COMPLETED".equals(row.status)) {
            return AcquireAttempt.result(CocoIdempotencyAcquireResult.replay(deserializeResponse(row)));
        }
        throw new IllegalStateException("JDBC idempotency row has an invalid status");
    }

    private Row selectForUpdate(Connection connection, String key, String expectedRequestHash,
            AtomicBoolean rowObserved) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(this.selectForUpdateSql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) return null;
                rowObserved.set(true);
                String requestHash = resultSet.getString(1);
                String status = resultSet.getString(3);
                String responseHeadersJson = null;
                byte[] responseBody = null;
                if ("COMPLETED".equals(status) && Objects.equals(requestHash, expectedRequestHash)) {
                    responseHeadersJson = readHeaders(resultSet.getCharacterStream(6));
                    InputStream bodyStream = resultSet.getBinaryStream(7);
                    responseBody = bodyStream == null ? null : readBody(bodyStream);
                }
                Row row = new Row(requestHash, resultSet.getString(2), status,
                        resultSet.getLong(4), resultSet.getObject(5, Integer.class), responseHeadersJson,
                        responseBody);
                if (resultSet.next()) throw new IllegalStateException("JDBC idempotency key is not unique");
                return row;
            }
        }
    }

    private String readHeaders(Reader source) throws SQLException {
        if (source == null) return null;
        int maximum = this.properties.getMaxHeaderBytes();
        int allocation = Math.min(maximum, 8_192);
        StringBuilder value = new StringBuilder(allocation);
        int bytesRead = 0;
        try (Reader reader = source) {
            int character;
            while ((character = reader.read()) >= 0) {
                char first = (char) character;
                int utf8Length;
                char second = 0;
                if (Character.isHighSurrogate(first)) {
                    int following = reader.read();
                    if (following < 0 || !Character.isLowSurrogate((char) following)) {
                        throw invalidStoredRow("JDBC completed idempotency headers contain invalid Unicode");
                    }
                    second = (char) following;
                    utf8Length = 4;
                }
                else if (Character.isLowSurrogate(first)) {
                    throw invalidStoredRow("JDBC completed idempotency headers contain invalid Unicode");
                }
                else if (first <= 0x7f) {
                    utf8Length = 1;
                }
                else if (first <= 0x7ff) {
                    utf8Length = 2;
                }
                else {
                    utf8Length = 3;
                }
                if (bytesRead > maximum - utf8Length) {
                    throw invalidStoredRow("JDBC completed idempotency headers exceed configured limit");
                }
                value.append(first);
                if (second != 0) value.append(second);
                bytesRead += utf8Length;
            }
        }
        catch (IOException ex) {
            throw lobReadFailure("headers", ex);
        }
        return value.toString();
    }

    private byte[] readBody(InputStream source) throws SQLException {
        Objects.requireNonNull(source, "source must not be null");
        int maximum = this.properties.getMaxResponseBytes();
        int allocation = Math.min(maximum + 1, 8_192);
        byte[] buffer = new byte[Math.max(1, allocation)];
        ByteArrayOutputStream output = new ByteArrayOutputStream(allocation);
        int total = 0;
        try (InputStream input = source) {
            while (total <= maximum) {
                int allowed = Math.min(buffer.length, maximum + 1 - total);
                int count = input.read(buffer, 0, allowed);
                if (count < 0) break;
                total += count;
                if (total > maximum) {
                    throw invalidStoredRow("JDBC completed idempotency body exceeds configured limit");
                }
                output.write(buffer, 0, count);
            }
        }
        catch (IOException ex) {
            throw lobReadFailure("body", ex);
        }
        return output.toByteArray();
    }

    private StoredResponse serializeResponse(CocoIdempotencyStoredResponse response) {
        if (response.status() < 100 || response.status() > 599) {
            throw new IllegalArgumentException("response status must be between 100 and 599");
        }
        byte[] body = response.body();
        if (body.length > this.properties.getMaxResponseBytes()) {
            throw new IllegalArgumentException("response body exceeds coco.idempotency.jdbc.max-response-bytes");
        }
        try {
            String headersJson = this.objectMapper.writeValueAsString(response.headers());
            if (headersJson.getBytes(StandardCharsets.UTF_8).length > this.properties.getMaxHeaderBytes()) {
                throw new IllegalArgumentException("response headers exceed coco.idempotency.jdbc.max-header-bytes");
            }
            return new StoredResponse(response.status(), headersJson, body);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("response headers cannot be serialized as structured JSON", ex);
        }
    }

    private CocoIdempotencyStoredResponse deserializeResponse(Row row) {
        if (row.responseStatus == null || row.responseHeadersJson == null || row.responseBody == null) {
            throw new IllegalStateException("JDBC completed idempotency row is incomplete");
        }
        if (row.responseStatus < 100 || row.responseStatus > 599
                || row.responseBody.length > this.properties.getMaxResponseBytes()) {
            throw new IllegalStateException("JDBC completed idempotency row exceeds configured response limits");
        }
        byte[] headerBytes = row.responseHeadersJson.getBytes(StandardCharsets.UTF_8);
        if (headerBytes.length > this.properties.getMaxHeaderBytes()) {
            throw new IllegalStateException("JDBC completed idempotency headers exceed configured limit");
        }
        try {
            JsonNode node = this.objectMapper.readTree(headerBytes);
            return new CocoIdempotencyStoredResponse(row.responseStatus, strictHeaders(node), row.responseBody);
        }
        catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("JDBC completed idempotency headers are invalid", ex);
        }
    }

    private static Map<String, List<String>> strictHeaders(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("headers must be an object");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isArray()) {
                throw new IllegalArgumentException("headers have an invalid shape");
            }
            List<String> values = new ArrayList<>();
            for (JsonNode value : entry.getValue()) {
                if (!value.isTextual()) throw new IllegalArgumentException("headers have an invalid shape");
                values.add(value.textValue());
            }
            headers.put(entry.getKey(), values);
        });
        return headers;
    }

    private void bindLease(PreparedStatement statement, int startIndex, CocoIdempotencyLease lease, Instant now)
            throws SQLException {
        statement.setString(startIndex, storageKey(lease.request()));
        statement.setString(startIndex + 1, lease.request().requestHash());
        statement.setString(startIndex + 2, lease.ownerToken());
        statement.setLong(startIndex + 3, lease.expiresAt().toEpochMilli());
        statement.setLong(startIndex + 4, now.toEpochMilli());
    }

    private String storageKey(CocoIdempotencyRequest request) {
        return this.properties.getKeyPrefix() + request.keyHash();
    }

    private <T> T inTransaction(SqlWork<T> work) {
        ensureOpen();
        Connection connection = null;
        Throwable failure = null;
        try {
            connection = requireConnection(this.dataSource.getConnection());
            return executeTransaction(connection, work);
        }
        catch (SQLException ex) {
            failure = ex;
            throw databaseFailure(ex);
        }
        catch (RuntimeException ex) {
            failure = ex;
            throw ex;
        }
        finally {
            if (connection != null) closeConnection(connection, failure);
        }
    }

    private <T> T executeTransaction(Connection connection, SqlWork<T> work) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        boolean readOnly = connection.isReadOnly();
        int isolation = connection.getTransactionIsolation();
        Throwable failure = null;
        boolean transactionStarted = false;
        try {
            connection.setReadOnly(false);
            connection.setAutoCommit(false);
            transactionStarted = true;
            T value = work.execute(connection);
            connection.commit();
            return value;
        }
        catch (SQLException | RuntimeException ex) {
            failure = ex;
            if (transactionStarted) rollback(connection, ex);
            throw ex;
        }
        finally {
            restoreConnection(connection, autoCommit, readOnly, isolation, failure);
        }
    }

    private static void rollback(Connection connection, Throwable primaryFailure) {
        try {
            connection.rollback();
        }
        catch (SQLException rollbackFailure) {
            primaryFailure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreConnection(Connection connection, boolean autoCommit, boolean readOnly, int isolation,
            Throwable primaryFailure) throws SQLException {
        try {
            if (connection.getTransactionIsolation() != isolation) connection.setTransactionIsolation(isolation);
            if (connection.isReadOnly() != readOnly) connection.setReadOnly(readOnly);
            if (connection.getAutoCommit() != autoCommit) connection.setAutoCommit(autoCommit);
        }
        catch (SQLException restoreFailure) {
            if (primaryFailure != null) primaryFailure.addSuppressed(restoreFailure);
            else throw restoreFailure;
        }
    }

    private static void closeConnection(Connection connection, Throwable primaryFailure) {
        try {
            connection.close();
        }
        catch (SQLException closeFailure) {
            if (primaryFailure != null) primaryFailure.addSuppressed(closeFailure);
            else throw databaseFailure(closeFailure);
        }
    }

    private static Connection requireConnection(Connection connection) {
        if (connection == null) throw new IllegalStateException("JDBC data source returned no connection");
        return connection;
    }

    private static boolean isDuplicateKey(SQLException exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.add(exception);
        for (int inspected = 0; inspected < MAX_SQL_EXCEPTION_NODES && !pending.isEmpty(); inspected++) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if ("23505".equals(sqlState)
                        || ("23000".equals(sqlState) && sqlException.getErrorCode() == 1062)) return true;
                if (sqlException.getNextException() != null) pending.addLast(sqlException.getNextException());
            }
            if (current.getCause() != null) pending.addLast(current.getCause());
        }
        return false;
    }

    private static SQLException sqlCause(DataAccessResourceFailureException exception) {
        if (exception.getCause() instanceof SQLException sqlException) return sqlException;
        throw exception;
    }

    private static DataSource requireIndependentDataSource(DataSource source) {
        DataSource checked = Objects.requireNonNull(source, "dataSource must not be null");
        Set<DataSource> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        DataSource current = checked;
        while (true) {
            if (!visited.add(current)) {
                throw new IllegalArgumentException("JDBC idempotency data source proxy chain must not contain a cycle");
            }
            if (current instanceof TransactionAwareDataSourceProxy || unwrapsTransactionAwareProxy(current)) {
                throw new IllegalArgumentException("JDBC idempotency data source must not be transaction-aware");
            }
            if (current instanceof DelegatingDataSource delegatingDataSource) {
                current = delegatingDataSource.getTargetDataSource();
                if (current == null) {
                    throw new IllegalArgumentException("JDBC idempotency delegating data source must have a target");
                }
            }
            else {
                break;
            }
        }
        return checked;
    }

    private static boolean unwrapsTransactionAwareProxy(DataSource dataSource) {
        try {
            return dataSource.isWrapperFor(TransactionAwareDataSourceProxy.class)
                    && dataSource.unwrap(TransactionAwareDataSourceProxy.class) != null;
        }
        catch (SQLException ex) {
            throw new IllegalArgumentException("JDBC idempotency data source proxy chain cannot be validated", ex);
        }
    }

    private static SQLException lobReadFailure(String field, IOException cause) {
        return new SQLException("JDBC completed idempotency " + field + " cannot be read", cause);
    }

    private static IllegalStateException invalidStoredRow(String message) {
        return new IllegalStateException(message);
    }

    private static String ownerToken() {
        byte[] value = new byte[32];
        OWNER_RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static DataAccessResourceFailureException databaseFailure(SQLException cause) {
        return new DataAccessResourceFailureException("JDBC idempotency database operation failed", cause);
    }

    private void ensureOpen() {
        if (this.closed.get()) throw new IllegalStateException("JDBC idempotency store is closed");
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private record AcquireAttempt(CocoIdempotencyAcquireResult result) {
        private static AcquireAttempt result(CocoIdempotencyAcquireResult result) { return new AcquireAttempt(result); }
        private static AcquireAttempt retry() { return new AcquireAttempt(null); }
    }

    private record InsertAttempt(boolean acquired, DataAccessResourceFailureException failure) {
        private static InsertAttempt success() { return new InsertAttempt(true, null); }
        private static InsertAttempt failed(DataAccessResourceFailureException failure) {
            return new InsertAttempt(false, Objects.requireNonNull(failure));
        }
    }

    private record Row(String requestHash, String ownerToken, String status, long expiresAtEpochMillis,
                       Integer responseStatus, String responseHeadersJson, byte[] responseBody) { }

    private record StoredResponse(int status, String headersJson, byte[] body) { }
}
