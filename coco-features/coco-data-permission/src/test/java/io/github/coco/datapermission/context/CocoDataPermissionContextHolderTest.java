package io.github.coco.datapermission.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.Callable;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 数据权限上下文持有器测试。
 * <p>
 * 验证数据权限上下文可以被捕获、恢复，并包装跨线程任务。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionContextHolderTest {

    @AfterEach
    void clearContext() {
        CocoDataPermissionContextHolder.clear();
    }

    @Test
    void capturedContextWrapsCallableAndRestoresCallerContext() throws Exception {
        CocoDataPermissionContextHolder.set(context("orders"));
        Callable<String> callable = CocoDataPermissionContextHolder.wrap(
                () -> CocoDataPermissionContextHolder.requireCurrent().rule("orders").orElseThrow().resource());
        CocoDataPermissionContextHolder.set(context("products"));

        assertEquals("orders", callable.call());
        assertEquals("products", CocoDataPermissionContextHolder.requireCurrent()
                .rule("products").orElseThrow().resource());
    }

    @Test
    void restoringEmptySnapshotClearsContextTemporarily() {
        CocoContextSnapshot snapshot = CocoDataPermissionContextHolder.capture();
        CocoDataPermissionContextHolder.set(context("products"));

        try (CocoContextScope ignored = CocoDataPermissionContextHolder.restore(snapshot)) {
            assertTrue(CocoDataPermissionContextHolder.current().isEmpty());
        }

        assertEquals("products", CocoDataPermissionContextHolder.requireCurrent()
                .rule("products").orElseThrow().resource());
    }

    private static CocoDataPermissionContext context(String resource) {
        return CocoDataPermissionContext.of(Set.of(CocoDataPermissionRule.all(resource)));
    }
}
