package io.github.coco.feature.security.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Callable;

import io.github.coco.common.context.CocoContextScope;
import io.github.coco.common.context.CocoContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 安全上下文持有器测试。
 * <p>
 * 验证安全上下文可以被捕获、恢复，并包装跨线程任务。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoSecurityContextHolderTest {

    @AfterEach
    void clearContext() {
        CocoSecurityContextHolder.clear();
    }

    @Test
    void capturedContextWrapsCallableAndRestoresCallerContext() throws Exception {
        CocoSecurityContextHolder.set(context("alice"));
        Callable<String> callable = CocoSecurityContextHolder.wrap(
                () -> CocoSecurityContextHolder.requireCurrent().principal().principalId());
        CocoSecurityContextHolder.set(context("bob"));

        assertEquals("alice", callable.call());
        assertEquals("bob", CocoSecurityContextHolder.requireCurrent().principal().principalId());
    }

    @Test
    void restoringEmptySnapshotClearsContextTemporarily() {
        CocoContextSnapshot snapshot = CocoSecurityContextHolder.capture();
        CocoSecurityContextHolder.set(context("bob"));

        try (CocoContextScope ignored = CocoSecurityContextHolder.restore(snapshot)) {
            assertTrue(CocoSecurityContextHolder.current().isEmpty());
        }

        assertEquals("bob", CocoSecurityContextHolder.requireCurrent().principal().principalId());
    }

    private static CocoSecurityContext context(String principalId) {
        return CocoSecurityContext.authenticated(CocoSecurityPrincipal.of(principalId, principalId));
    }
}
