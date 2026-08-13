package io.github.coco.feature.idempotency.redis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;

/**
 * 基于 Redis Lua 的多实例幂等共享存储。
 *
 * <p>所有状态转换在 Redis 服务端脚本中完成，过期判断使用执行脚本的 Redis 节点时钟。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class RedisCocoIdempotencyStore implements CocoIdempotencyStore {

    private static final byte[] ACQUIRE_SCRIPT = script("""
            -- COCO_IDEMPOTENCY_ACQUIRE_V1
            redis.replicate_commands()
            local function normalize(value)
              value = string.gsub(value, '^0+', '')
              if value == '' then return '0' end
              return value
            end
            local function greater(left, right)
              left = normalize(left)
              right = normalize(right)
              return #left > #right or (#left == #right and left > right)
            end
            local function subtract(deadline, now)
              deadline = normalize(deadline)
              now = normalize(now)
              local borrow = 0
              local result = ''
              for index = #deadline, 1, -1 do
                local digit = string.byte(deadline, index) - 48 - borrow
                local now_index = #now - (#deadline - index)
                local now_digit = now_index > 0 and string.byte(now, now_index) - 48 or 0
                if digit < now_digit then digit = digit + 10; borrow = 1 else borrow = 0 end
                result = string.char(digit - now_digit + 48) .. result
              end
              return normalize(result)
            end
            local time = redis.call('TIME')
            local now = time[1] .. string.format('%03d', math.floor(tonumber(time[2]) / 1000))
            local current_expiry = redis.call('HGET', KEYS[1], 'expiresAt')
            local request_hash = redis.call('HGET', KEYS[1], 'requestHash')
            if current_expiry and greater(current_expiry, now) then
              if request_hash ~= ARGV[1] then return {'PAYLOAD_MISMATCH'} end
              local state = redis.call('HGET', KEYS[1], 'state')
              if state == 'IN_PROGRESS' then return {'IN_PROGRESS'} end
              if state == 'COMPLETED' then
                local response = redis.call('HGET', KEYS[1], 'response')
                if not response then return {'INVALID'} end
                return {'REPLAY', response}
              end
              return {'INVALID'}
            end
            if not greater(ARGV[2], now) then return {'INVALID'} end
            local ttl = subtract(ARGV[2], now)
            if ttl == '0' then return {'INVALID'} end
            redis.call('HSET', KEYS[1], 'state', 'IN_PROGRESS', 'requestHash', ARGV[1],
              'ownerToken', ARGV[3], 'expiresAt', ARGV[2])
            redis.call('HDEL', KEYS[1], 'response')
            redis.call('PEXPIRE', KEYS[1], ttl)
            return {'ACQUIRED', ARGV[3], ARGV[2]}
            """);

    private static final byte[] COMPLETE_SCRIPT = script("""
            -- COCO_IDEMPOTENCY_COMPLETE_V1
            redis.replicate_commands()
            local function normalize(value)
              value = string.gsub(value, '^0+', '')
              if value == '' then return '0' end
              return value
            end
            local function greater(left, right)
              left = normalize(left)
              right = normalize(right)
              return #left > #right or (#left == #right and left > right)
            end
            local time = redis.call('TIME')
            local now = time[1] .. string.format('%03d', math.floor(tonumber(time[2]) / 1000))
            local state = redis.call('HGET', KEYS[1], 'state')
            local request_hash = redis.call('HGET', KEYS[1], 'requestHash')
            local owner_token = redis.call('HGET', KEYS[1], 'ownerToken')
            local expires_at = redis.call('HGET', KEYS[1], 'expiresAt')
            if state ~= 'IN_PROGRESS' or request_hash ~= ARGV[1] or owner_token ~= ARGV[2]
              or expires_at ~= ARGV[3] or not expires_at or not greater(expires_at, now) then return 0 end
            redis.call('HSET', KEYS[1], 'state', 'COMPLETED', 'response', ARGV[4])
            return 1
            """);

    private static final byte[] FAIL_SCRIPT = script("""
            -- COCO_IDEMPOTENCY_FAIL_V1
            redis.replicate_commands()
            local function normalize(value)
              value = string.gsub(value, '^0+', '')
              if value == '' then return '0' end
              return value
            end
            local function greater(left, right)
              left = normalize(left)
              right = normalize(right)
              return #left > #right or (#left == #right and left > right)
            end
            local time = redis.call('TIME')
            local now = time[1] .. string.format('%03d', math.floor(tonumber(time[2]) / 1000))
            local state = redis.call('HGET', KEYS[1], 'state')
            local request_hash = redis.call('HGET', KEYS[1], 'requestHash')
            local owner_token = redis.call('HGET', KEYS[1], 'ownerToken')
            local expires_at = redis.call('HGET', KEYS[1], 'expiresAt')
            if state ~= 'IN_PROGRESS' or request_hash ~= ARGV[1] or owner_token ~= ARGV[2]
              or expires_at ~= ARGV[3] or not expires_at or not greater(expires_at, now) then return 0 end
            return redis.call('DEL', KEYS[1])
            """);

    private static final SecureRandom OWNER_RANDOM = new SecureRandom();

    private final RedisConnectionFactory connectionFactory;

    private final CocoIdempotencyRedisProperties properties;

    private final ObjectMapper objectMapper;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 Redis 幂等存储。
     *
     * @param connectionFactory Redis 连接工厂
     * @param properties Redis 适配器配置
     */
    public RedisCocoIdempotencyStore(RedisConnectionFactory connectionFactory,
            CocoIdempotencyRedisProperties properties) {
        this(connectionFactory, properties, new ObjectMapper());
    }

    /**
     * 创建 Redis 幂等存储并使用指定的结构化 JSON 编解码器。
     *
     * @param connectionFactory Redis 连接工厂
     * @param properties Redis 适配器配置
     * @param objectMapper JSON 编解码器
     */
    public RedisCocoIdempotencyStore(RedisConnectionFactory connectionFactory,
            CocoIdempotencyRedisProperties properties, ObjectMapper objectMapper) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.properties.validate();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /** {@inheritDoc} */
    @Override
    public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) {
        CocoIdempotencyRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Instant checkedExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        ensureOpen();
        String ownerToken = ownerToken();
        List<?> result = eval(ACQUIRE_SCRIPT, ReturnType.MULTI, redisKey(checkedRequest.keyHash()),
                checkedRequest.requestHash(), epochMillis(checkedExpiresAt), ownerToken);
        if (result.isEmpty()) {
            throw invalidScriptResult("acquire returned no status");
        }
        String status = text(result.get(0));
        return switch (status) {
            case "ACQUIRED" -> acquired(result, checkedRequest, checkedExpiresAt);
            case "IN_PROGRESS" -> CocoIdempotencyAcquireResult.inProgress();
            case "PAYLOAD_MISMATCH" -> CocoIdempotencyAcquireResult.payloadMismatch();
            case "REPLAY" -> replay(result);
            default -> throw invalidScriptResult("acquire returned " + status);
        };
    }

    /** {@inheritDoc} */
    @Override
    public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) {
        CocoIdempotencyLease checkedLease = Objects.requireNonNull(lease, "lease must not be null");
        CocoIdempotencyStoredResponse checkedResponse = Objects.requireNonNull(response, "response must not be null");
        Objects.requireNonNull(now, "now must not be null");
        byte[] responseBytes = serializeResponse(checkedResponse);
        ensureOpen();
        Long result = evalInteger(COMPLETE_SCRIPT, redisKey(checkedLease.request().keyHash()),
                checkedLease.request().requestHash(), checkedLease.ownerToken(), epochMillis(checkedLease.expiresAt()),
                new String(responseBytes, StandardCharsets.UTF_8));
        return binaryBoolean(result, "complete");
    }

    /** {@inheritDoc} */
    @Override
    public boolean fail(CocoIdempotencyLease lease, Instant now) {
        CocoIdempotencyLease checkedLease = Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(now, "now must not be null");
        ensureOpen();
        Long result = evalInteger(FAIL_SCRIPT, redisKey(checkedLease.request().keyHash()),
                checkedLease.request().requestHash(), checkedLease.ownerToken(), epochMillis(checkedLease.expiresAt()));
        return binaryBoolean(result, "fail");
    }

    /** 关闭存储；关闭后所有操作均拒绝。 */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private static byte[] script(String source) {
        return source.getBytes(StandardCharsets.UTF_8);
    }

    private static String epochMillis(Instant instant) {
        return Long.toString(instant.toEpochMilli());
    }

    private static String ownerToken() {
        byte[] bytes = new byte[32];
        OWNER_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static byte[] redisKey(String keyHash, String prefix) {
        return (prefix + keyHash).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] redisKey(String keyHash) {
        return redisKey(keyHash, this.properties.getKeyPrefix());
    }

    private CocoIdempotencyAcquireResult acquired(List<?> result, CocoIdempotencyRequest request,
            Instant fallbackExpiresAt) {
        if (result.size() != 3) {
            throw invalidScriptResult("acquire ACQUIRED result has an invalid shape");
        }
        String token = text(result.get(1));
        String expiresAt = text(result.get(2));
        if (token.isBlank() || !expiresAt.equals(epochMillis(fallbackExpiresAt))) {
            throw invalidScriptResult("acquire ACQUIRED result has invalid lease data");
        }
        return CocoIdempotencyAcquireResult.acquired(new CocoIdempotencyLease(request, token, fallbackExpiresAt));
    }

    private CocoIdempotencyAcquireResult replay(List<?> result) {
        if (result.size() != 2) {
            throw invalidScriptResult("acquire REPLAY result has an invalid shape");
        }
        return CocoIdempotencyAcquireResult.replay(deserializeResponse(bytes(result.get(1))));
    }

    private byte[] serializeResponse(CocoIdempotencyStoredResponse response) {
        if (response.body().length > this.properties.getMaxResponseBytes()) {
            throw new IllegalArgumentException("response body exceeds coco.idempotency.redis.max-response-bytes");
        }
        try {
            ObjectNode headersOnly = this.objectMapper.createObjectNode();
            headersOnly.set("headers", this.objectMapper.valueToTree(response.headers()));
            byte[] headerBytes = this.objectMapper.writeValueAsBytes(headersOnly);
            if (headerBytes.length > this.properties.getMaxHeaderBytes()) {
                throw new IllegalArgumentException("response headers exceed coco.idempotency.redis.max-header-bytes");
            }
            ObjectNode root = this.objectMapper.createObjectNode();
            root.put("status", response.status());
            root.set("headers", this.objectMapper.valueToTree(response.headers()));
            root.set("body", this.objectMapper.getNodeFactory().binaryNode(response.body()));
            byte[] serialized = this.objectMapper.writeValueAsBytes(root);
            if (serialized.length > this.properties.getMaxResponseBytes()) {
                throw new IllegalArgumentException("serialized response exceeds coco.idempotency.redis.max-response-bytes");
            }
            return serialized;
        }
        catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("response cannot be serialized as structured JSON", ex);
        }
    }

    private CocoIdempotencyStoredResponse deserializeResponse(byte[] serialized) {
        if (serialized.length > this.properties.getMaxResponseBytes()) {
            throw invalidScriptResult("Redis response exceeds the configured response limit");
        }
        try {
            JsonNode root = this.objectMapper.readTree(serialized);
            if (root == null || !root.isObject() || !root.has("status") || !root.has("headers")
                    || !root.has("body") || !root.get("headers").isObject() || !root.get("body").isTextual()
                    || !root.get("status").canConvertToInt()) {
                throw invalidScriptResult("Redis response JSON has an invalid shape");
            }
            byte[] headerBytes = this.objectMapper.writeValueAsBytes(root.get("headers"));
            if (headerBytes.length > this.properties.getMaxHeaderBytes()) {
                throw invalidScriptResult("Redis response headers exceed the configured limit");
            }
            Map<String, List<String>> headers = this.objectMapper.readValue(headerBytes,
                    new TypeReference<Map<String, List<String>>>() { });
            byte[] body = root.get("body").binaryValue();
            if (body.length > this.properties.getMaxResponseBytes()) {
                throw invalidScriptResult("Redis response body exceeds the configured limit");
            }
            return new CocoIdempotencyStoredResponse(root.get("status").intValue(), headers, body);
        }
        catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw invalidScriptResult("Redis response JSON is invalid", ex);
        }
    }

    private List<?> eval(byte[] script, ReturnType returnType, byte[] key, String... arguments) {
        ensureOpen();
        RedisConnection connection = requireConnection(this.connectionFactory.getConnection());
        byte[][] values = new byte[arguments.length + 1][];
        values[0] = key;
        for (int index = 0; index < arguments.length; index++) {
            values[index + 1] = arguments[index].getBytes(StandardCharsets.UTF_8);
        }
        try {
            Object result = connection.scriptingCommands().eval(script, returnType, 1, values);
            if (!(result instanceof List<?> list)) {
                throw invalidScriptResult("Redis script returned a non-list result");
            }
            return list;
        }
        finally {
            connection.close();
        }
    }

    private Long evalInteger(byte[] script, byte[] key, String... arguments) {
        ensureOpen();
        RedisConnection connection = requireConnection(this.connectionFactory.getConnection());
        byte[][] values = new byte[arguments.length + 1][];
        values[0] = key;
        for (int index = 0; index < arguments.length; index++) {
            values[index + 1] = arguments[index].getBytes(StandardCharsets.UTF_8);
        }
        try {
            Object result = connection.scriptingCommands().eval(script, ReturnType.INTEGER, 1, values);
            if (!(result instanceof Number number)) {
                throw invalidScriptResult("Redis script returned a non-integer result");
            }
            return number.longValue();
        }
        finally {
            connection.close();
        }
    }

    private static String text(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof String string) {
            return string;
        }
        throw invalidScriptResult("Redis script returned a non-text value");
    }

    private static byte[] bytes(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof String string) {
            return string.getBytes(StandardCharsets.UTF_8);
        }
        throw invalidScriptResult("Redis script returned a non-binary response");
    }

    private static boolean binaryBoolean(Long result, String operation) {
        if (result == null || (result != 0 && result != 1)) {
            throw invalidScriptResult(operation + " returned an invalid integer");
        }
        return result == 1;
    }

    private static RedisConnection requireConnection(RedisConnection connection) {
        if (connection == null) {
            throw new IllegalStateException("Redis connection factory returned no connection");
        }
        return connection;
    }

    private static IllegalStateException invalidScriptResult(String message) {
        return new IllegalStateException(message);
    }

    private static IllegalStateException invalidScriptResult(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Redis idempotency store is closed");
        }
    }
}
