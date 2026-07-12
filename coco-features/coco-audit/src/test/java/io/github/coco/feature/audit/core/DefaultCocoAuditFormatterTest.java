package io.github.coco.feature.audit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Coco 默认审计事件格式化器测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class DefaultCocoAuditFormatterTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-10T01:02:03Z");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CocoAuditFormatter formatter = new DefaultCocoAuditFormatter();

    @Test
    void formatsStableJsonWithFixedFieldsAndSortedAttributes() {
        CocoAuditEvent event = CocoAuditEvent.builder("business-operation")
                .action("update")
                .resourceType("order")
                .resourceId("1001")
                .traceId("trace-1")
                .actor("alice")
                .tenantId("tenant-a")
                .success(false)
                .occurredAt(OCCURRED_AT)
                .attribute("zeta", "last")
                .attribute("alpha", 7)
                .attribute("middle", true)
                .build();

        assertThat(this.formatter.format(event)).isEqualTo("{\"type\":\"business-operation\",\"action\":\"update\","
                + "\"resourceType\":\"order\",\"resourceId\":\"1001\",\"traceId\":\"trace-1\","
                + "\"actor\":\"alice\",\"tenantId\":\"tenant-a\",\"success\":false,"
                + "\"occurredAt\":\"2026-07-10T01:02:03Z\","
                + "\"attributes\":{\"alpha\":7,\"middle\":true,\"zeta\":\"last\"}}");
    }

    @Test
    void escapesUnsafeCharactersAsValidSingleLineJson() throws Exception {
        String unsafe = "quote\" slash\\ backspace\b formfeed\f newline\n carriage\r tab\t control"
                + (char) 1 + " line" + (char) 0x2028 + " paragraph" + (char) 0x2029;
        CocoAuditEvent event = CocoAuditEvent.builder(unsafe)
                .occurredAt(OCCURRED_AT)
                .attribute("unsafe", unsafe)
                .build();

        String json = this.formatter.format(event);
        JsonNode parsed = OBJECT_MAPPER.readTree(json);

        assertThat(parsed.path("type").asText()).isEqualTo(unsafe);
        assertThat(parsed.path("attributes").path("unsafe").asText()).isEqualTo(unsafe);
        assertThat(json).contains("\\\"", "\\\\", "\\b", "\\f", "\\n", "\\r", "\\t",
                "\\u0001", "\\u2028", "\\u2029");
        assertThat(json).doesNotContain("\b", "\f", "\n", "\r", "\t", Character.toString(1),
                Character.toString(0x2028), Character.toString(0x2029));
    }

    @Test
    void keepsValidNumbersAndEscapesInvalidUnicodeScalars() throws Exception {
        String unpairedHighSurrogate = Character.toString((char) 0xd800);
        String unpairedLowSurrogate = Character.toString((char) 0xdc00);
        CocoAuditEvent event = CocoAuditEvent.builder("attribute-types")
                .occurredAt(OCCURRED_AT)
                .attribute("decimal", new BigDecimal("12.50"))
                .attribute("atomic", new AtomicInteger(3))
                .attribute("notFinite", Double.NaN)
                .attribute("unpairedHigh", unpairedHighSurrogate)
                .attribute("unpairedLow", unpairedLowSurrogate)
                .build();

        String json = this.formatter.format(event);
        JsonNode parsed = OBJECT_MAPPER.readTree(json);

        assertThat(parsed.path("attributes").path("decimal").decimalValue())
                .isEqualByComparingTo("12.50");
        assertThat(parsed.path("attributes").path("atomic").intValue()).isEqualTo(3);
        assertThat(parsed.path("attributes").path("notFinite").asText()).isEqualTo("NaN");
        assertThat(json).contains("\"unpairedHigh\":\"\\ud800\"", "\"unpairedLow\":\"\\udc00\"");
        assertThat(json).doesNotContain(unpairedHighSurrogate, unpairedLowSurrogate);
    }
}
