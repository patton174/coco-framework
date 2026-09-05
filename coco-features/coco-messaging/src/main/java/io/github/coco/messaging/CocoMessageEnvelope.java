package io.github.coco.messaging;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.messaging.support.CocoMessagingMessages;

/**
 * Coco 消息信封。
 * <p>
 * 消息信封是传输层和处理器之间的稳定边界。负载由业务方定义，路由信息、时间和 Headers 在创建后不可变。
 * </p>
 */
public final class CocoMessageEnvelope {

    /** 最大 topic 长度。 */
    public static final int MAX_TOPIC_LENGTH = 128;

    /** 最大 Header 数量。 */
    public static final int MAX_HEADER_COUNT = 32;

    /** 最大 Header 键长度。 */
    public static final int MAX_HEADER_NAME_LENGTH = 64;

    /** 最大 Header 值长度。 */
    public static final int MAX_HEADER_VALUE_LENGTH = 1024;

    private final String messageId;

    private final String topic;

    private final Instant timestamp;

    private final String traceId;

    private final Map<String, String> headers;

    private final Object payload;

    /**
     * 创建消息信封。
     * @param messageId 消息标识
     * @param topic 主题
     * @param timestamp 创建时间
     * @param traceId TraceId；允许为空
     * @param headers 自定义 Headers
     * @param payload 业务负载；允许为空
     */
    public CocoMessageEnvelope(String messageId, String topic, Instant timestamp, String traceId,
            Map<String, String> headers, Object payload) {
        this.messageId = requireIdentifier(messageId, "coco.messaging.error.message-id-invalid");
        this.topic = requireTopic(topic);
        this.timestamp = requireTimestamp(timestamp);
        this.traceId = traceId == null ? null : requireIdentifier(traceId, "coco.messaging.error.trace-id-invalid");
        this.headers = immutableHeaders(headers);
        this.payload = payload;
    }

    /**
     * 使用当前 Trace 上下文创建消息信封。
     * @param topic 主题
     * @param payload 业务负载
     * @return 新消息信封
     */
    public static CocoMessageEnvelope create(String topic, Object payload) {
        return new CocoMessageEnvelope(UUID.randomUUID().toString(), topic, Instant.now(),
                CocoTraceContext.currentTraceId().orElse(null), Map.of(), payload);
    }

    /** @return 消息标识 */
    public String messageId() {
        return this.messageId;
    }

    /** @return 消息主题 */
    public String topic() {
        return this.topic;
    }

    /** @return 创建时间 */
    public Instant timestamp() {
        return this.timestamp;
    }

    /** @return 创建消息时捕获的 TraceId；可能为空 */
    public String traceId() {
        return this.traceId;
    }

    /** @return 不可变的消息 Headers */
    public Map<String, String> headers() {
        return this.headers;
    }

    /** @return 业务负载；可能为空 */
    public Object payload() {
        return this.payload;
    }

    private static String requireTopic(String value) {
        String checked = requireIdentifier(value, "coco.messaging.error.topic-invalid");
        if (checked.length() > MAX_TOPIC_LENGTH || !checked.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw CocoMessagingException.invalidArgument("coco.messaging.error.topic-invalid");
        }
        return checked;
    }

    private static String requireIdentifier(String value, String messageCode) {
        if (value == null || value.isBlank()) {
            throw CocoMessagingException.invalidArgument(messageCode);
        }
        String checked = value.trim();
        if (checked.length() > MAX_TOPIC_LENGTH || containsControlCharacter(checked)) {
            throw CocoMessagingException.invalidArgument(messageCode);
        }
        return checked;
    }

    private static Instant requireTimestamp(Instant value) {
        if (value == null) {
            throw CocoMessagingException.invalidArgument("coco.messaging.error.timestamp-required");
        }
        return value;
    }

    private static Map<String, String> immutableHeaders(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > MAX_HEADER_COUNT) {
            throw CocoMessagingException.invalidArgument("coco.messaging.error.headers-too-many", MAX_HEADER_COUNT);
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> {
            if (name == null || name.isBlank() || name.length() > MAX_HEADER_NAME_LENGTH
                    || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                throw CocoMessagingException.invalidArgument("coco.messaging.error.header-name-invalid");
            }
            if (value == null || value.length() > MAX_HEADER_VALUE_LENGTH || containsControlCharacter(value)) {
                throw CocoMessagingException.invalidArgument("coco.messaging.error.header-value-invalid");
            }
            copy.put(name, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
