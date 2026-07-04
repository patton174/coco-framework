package io.github.coco.common.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco Trace 上下文测试。
 * <p>
 * 验证 TraceId 可以在当前线程中稳定保存、自动创建、临时切换并在结束后恢复，为后续日志和审计能力提供基础上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTraceContextTest {

    @AfterEach
    void clearTraceContext() {
        CocoTraceContext.clear();
    }

    @Test
    void returnsEmptyWhenTraceIdIsNotSet() {
        assertTrue(CocoTraceContext.currentTraceId().isEmpty());
    }

    @Test
    void setTraceIdStoresTrimmedValueAndReturnsIt() {
        String traceId = CocoTraceContext.setTraceId(" trace-001 ");

        assertEquals("trace-001", traceId);
        assertEquals("trace-001", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void setTraceIdRejectsBlankValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> CocoTraceContext.setTraceId(" "));

        assertEquals("traceId must not be blank", exception.getMessage());
    }

    @Test
    void getOrCreateTraceIdCreatesStableLowerHexValue() {
        String first = CocoTraceContext.getOrCreateTraceId();
        String second = CocoTraceContext.getOrCreateTraceId();

        assertEquals(first, second);
        assertTrue(first.matches("[0-9a-f]{32}"));
    }

    @Test
    void clearRemovesCurrentTraceId() {
        CocoTraceContext.setTraceId("trace-001");

        CocoTraceContext.clear();

        assertTrue(CocoTraceContext.currentTraceId().isEmpty());
    }

    @Test
    void runWithTraceIdRestoresPreviousValue() {
        CocoTraceContext.setTraceId("outer");

        CocoTraceContext.runWithTraceId("inner", () -> assertEquals("inner",
                CocoTraceContext.currentTraceId().orElseThrow()));

        assertEquals("outer", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void runWithTraceIdClearsWhenNoPreviousValueExists() {
        CocoTraceContext.runWithTraceId("inner", () -> assertEquals("inner",
                CocoTraceContext.currentTraceId().orElseThrow()));

        assertTrue(CocoTraceContext.currentTraceId().isEmpty());
    }

    @Test
    void runWithTraceIdRestoresPreviousValueWhenRunnableThrows() {
        CocoTraceContext.setTraceId("outer");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CocoTraceContext.runWithTraceId("inner", () -> {
                    throw new IllegalStateException("boom");
                }));

        assertEquals("boom", exception.getMessage());
        assertEquals("outer", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void callWithTraceIdReturnsSupplierValueAndRestoresPreviousValue() {
        CocoTraceContext.setTraceId("outer");

        String result = CocoTraceContext.callWithTraceId("inner", () -> CocoTraceContext.currentTraceId().orElseThrow());

        assertEquals("inner", result);
        assertEquals("outer", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void generatedTraceIdsAreDifferentAcrossClearedContexts() {
        String first = CocoTraceContext.getOrCreateTraceId();
        CocoTraceContext.clear();

        String second = CocoTraceContext.getOrCreateTraceId();

        assertNotEquals(first, second);
    }

    @Test
    void traceIdGeneratorCreatesLowerHexValue() {
        String traceId = CocoTraceIdGenerator.generate();

        assertFalse(traceId.isBlank());
        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }
}
