package io.github.coco.feature.tenant.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 租户上下文持有器测试。
 * <p>
 * 验证租户上下文可以被捕获、恢复，并包装跨线程任务。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTenantContextHolderTest {

    @AfterEach
    void clearContext() {
        CocoTenantContextHolder.clear();
    }

    @Test
    void capturedContextWrapsCallableAndRestoresCallerContext() throws Exception {
        CocoTenantContextHolder.set(CocoTenantContext.of("tenant-a", "Tenant A"));
        Callable<String> callable = CocoTenantContextHolder.wrap(
                () -> CocoTenantContextHolder.requireCurrent().tenantId());
        CocoTenantContextHolder.set(CocoTenantContext.of("tenant-b", "Tenant B"));

        assertEquals("tenant-a", callable.call());
        assertEquals("tenant-b", CocoTenantContextHolder.requireCurrent().tenantId());
    }

    @Test
    void restoringEmptySnapshotClearsContextTemporarily() {
        CocoContextSnapshot snapshot = CocoTenantContextHolder.capture();
        CocoTenantContextHolder.set(CocoTenantContext.of("tenant-b", "Tenant B"));

        try (CocoContextScope ignored = CocoTenantContextHolder.restore(snapshot)) {
            assertTrue(CocoTenantContextHolder.current().isEmpty());
        }

        assertEquals("tenant-b", CocoTenantContextHolder.requireCurrent().tenantId());
    }
}
