package io.github.coco.feature.security.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.coco.common.context.CocoContextScope;
import io.github.coco.common.context.CocoContextSnapshot;
import io.github.coco.common.exception.type.CocoUnauthorizedException;
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

    @Test
    void contextIsThreadLocalAndDoesNotLeakAcrossWorkerThreads() throws Exception {
        CocoSecurityContextHolder.set(context("caller"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> workerInitiallyEmpty = executor.submit(
                    () -> CocoSecurityContextHolder.current().isEmpty());
            Future<String> workerPrincipal = executor.submit(() -> {
                CocoSecurityContextHolder.set(context("worker"));
                try {
                    return CocoSecurityContextHolder.requireCurrent().principal().principalId();
                }
                finally {
                    CocoSecurityContextHolder.clear();
                }
            });

            assertTrue(workerInitiallyEmpty.get());
            assertEquals("worker", workerPrincipal.get());
            assertEquals("caller", CocoSecurityContextHolder.requireCurrent().principal().principalId());
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void wrappedRunnableRestoresWorkerContextAfterException() throws Exception {
        CocoSecurityContextHolder.set(context("captured"));
        Runnable source = () -> {
            assertEquals("captured", CocoSecurityContextHolder.requireCurrent().principal().principalId());
            throw new IllegalStateException("boom");
        };
        Runnable runnable = CocoSecurityContextHolder.wrap(source);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> workerPrincipal = executor.submit(() -> {
                CocoSecurityContextHolder.set(context("worker"));
                try {
                    IllegalStateException exception = assertThrows(IllegalStateException.class, runnable::run);
                    assertEquals("boom", exception.getMessage());
                    return CocoSecurityContextHolder.requireCurrent().principal().principalId();
                }
                finally {
                    CocoSecurityContextHolder.clear();
                }
            });

            assertEquals("worker", workerPrincipal.get());
            assertEquals("captured", CocoSecurityContextHolder.requireCurrent().principal().principalId());
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void runWithContextRestoresPreviousContextAfterException() {
        CocoSecurityContextHolder.set(context("caller"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CocoSecurityContextHolder.runWithContext(context("temporary"), () -> {
                    assertEquals("temporary", CocoSecurityContextHolder.requireCurrent().principal().principalId());
                    throw new IllegalStateException("failed");
                }));

        assertEquals("failed", exception.getMessage());
        assertEquals("caller", CocoSecurityContextHolder.requireCurrent().principal().principalId());
    }

    @Test
    void missingContextThrowsUnauthorizedException() {
        CocoSecurityContextHolder.clear();

        CocoUnauthorizedException exception = assertThrows(CocoUnauthorizedException.class,
                CocoSecurityContextHolder::requireCurrent);

        assertEquals("coco.feature.security.error.context-missing", exception.message().code());
    }

    @Test
    void rejectsNullInputs() {
        NullPointerException setException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.set(null));
        NullPointerException restoreException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.restore(null));
        NullPointerException runnableException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.wrap((Runnable) null));
        NullPointerException callableException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.wrap((Callable<?>) null));
        NullPointerException supplierException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.wrapSupplier(null));
        NullPointerException runException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.runWithContext(context("alice"), null));
        NullPointerException callException = assertThrows(NullPointerException.class,
                () -> CocoSecurityContextHolder.callWithContext(context("alice"), null));

        assertEquals("securityContext must not be null", setException.getMessage());
        assertEquals("snapshot must not be null", restoreException.getMessage());
        assertEquals("runnable must not be null", runnableException.getMessage());
        assertEquals("callable must not be null", callableException.getMessage());
        assertEquals("supplier must not be null", supplierException.getMessage());
        assertEquals("runnable must not be null", runException.getMessage());
        assertEquals("supplier must not be null", callException.getMessage());
    }

    @Test
    void setReturnsSameContextInstance() {
        CocoSecurityContext securityContext = context("alice");

        CocoSecurityContext returned = CocoSecurityContextHolder.set(securityContext);

        assertSame(securityContext, returned);
        assertFalse(CocoSecurityContextHolder.current().isEmpty());
    }

    private static CocoSecurityContext context(String principalId) {
        return CocoSecurityContext.authenticated(new CocoSecurityPrincipal(principalId, principalId,
                Set.of("role-" + principalId), Set.of("permission-" + principalId), Map.of()));
    }
}
