package io.github.coco.logging.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CocoMdcContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void wrappedTaskPropagatesMdcAndRestoresWorkerMdcBeforeThreadReuse() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                MDC.setContextMap(Map.of("workerOnly", "worker"));
                return null;
            }).get();

            MDC.setContextMap(Map.of("request", "captured", "capturedOnly", "yes"));
            Runnable wrapped = CocoMdcContext.wrap(() -> {
                assertEquals(Map.of("request", "captured", "capturedOnly", "yes"), currentMdc());
            });
            MDC.setContextMap(Map.of("callerOnly", "caller"));

            executor.submit(wrapped).get();

            assertEquals(Map.of("workerOnly", "worker"),
                    executor.submit(CocoMdcContextTest::currentMdc).get());
        }
        finally {
            executor.shutdownNow();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    void wrappedCallableAndSupplierRestoreCallerMdc() throws Exception {
        MDC.setContextMap(Map.of("request", "captured"));
        Callable<String> callable = CocoMdcContext.wrap(() -> MDC.get("request"));
        Supplier<String> supplier = CocoMdcContext.wrapSupplier(() -> MDC.get("request"));
        MDC.setContextMap(Map.of("request", "caller"));

        assertEquals("captured", callable.call());
        assertEquals(Map.of("request", "caller"), currentMdc());
        assertEquals("captured", supplier.get());
        assertEquals(Map.of("request", "caller"), currentMdc());
    }

    @Test
    void nestedCallWithContextOverridesAndRestoresMdc() {
        Map<String, String> caller = Map.of("request", "caller", "callerOnly", "yes");
        Map<String, String> outer = Map.of("request", "outer", "outerOnly", "yes");
        Map<String, String> inner = Map.of("request", "inner", "innerOnly", "yes");
        MDC.setContextMap(caller);

        String result = CocoMdcContext.callWithContext(outer, () -> {
            assertEquals(outer, currentMdc());
            String nestedResult = CocoMdcContext.callWithContext(inner, () -> {
                assertEquals(inner, currentMdc());
                return MDC.get("request");
            });
            assertEquals(outer, currentMdc());
            return nestedResult;
        });

        assertEquals("inner", result);
        assertEquals(caller, currentMdc());
    }

    @Test
    void runWithContextRestoresPreviousMdcAfterException() {
        Map<String, String> caller = Map.of("request", "caller", "callerOnly", "yes");
        Map<String, String> temporary = Map.of("request", "temporary");
        MDC.setContextMap(caller);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CocoMdcContext.runWithContext(temporary, () -> {
                    assertEquals(temporary, currentMdc());
                    throw new IllegalStateException("failed");
                }));

        assertEquals("failed", exception.getMessage());
        assertEquals(caller, currentMdc());
    }

    @Test
    void wrappedTaskRestoresWorkerMdcAfterException() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                MDC.setContextMap(Map.of("workerOnly", "worker"));
                return null;
            }).get();

            MDC.setContextMap(Map.of("request", "captured"));
            Runnable wrapped = CocoMdcContext.wrap((Runnable) () -> {
                assertEquals("captured", MDC.get("request"));
                assertNull(MDC.get("workerOnly"));
                throw new IllegalStateException("boom");
            });
            MDC.setContextMap(Map.of("request", "caller"));

            Map<String, String> workerAfter = executor.submit(() -> {
                IllegalStateException exception = assertThrows(IllegalStateException.class, wrapped::run);
                assertEquals("boom", exception.getMessage());
                return currentMdc();
            }).get();

            assertEquals(Map.of("workerOnly", "worker"), workerAfter);
            assertEquals(Map.of("request", "caller"), currentMdc());
        }
        finally {
            executor.shutdownNow();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    void restoringEmptySnapshotClearsMdcTemporarily() {
        MDC.clear();
        CocoContextSnapshot snapshot = CocoMdcContext.capture();
        MDC.setContextMap(Map.of("request", "target"));

        try (CocoContextScope ignored = CocoMdcContext.restore(snapshot)) {
            assertNull(MDC.get("request"));
            Map<String, String> current = MDC.getCopyOfContextMap();
            assertTrue(current == null || current.isEmpty());
        }

        assertEquals(Map.of("request", "target"), currentMdc());
    }

    @Test
    void rejectsNullInputs() {
        NullPointerException restoreException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.restore(null));
        NullPointerException runnableException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.wrap((Runnable) null));
        NullPointerException callableException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.wrap((Callable<?>) null));
        NullPointerException supplierException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.wrapSupplier(null));
        NullPointerException runContextException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.runWithContext(null, () -> {
                }));
        NullPointerException runRunnableException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.runWithContext(Map.of(), null));
        NullPointerException callContextException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.callWithContext(null, () -> "value"));
        NullPointerException callSupplierException = assertThrows(NullPointerException.class,
                () -> CocoMdcContext.callWithContext(Map.of(), null));

        assertEquals("snapshot must not be null", restoreException.getMessage());
        assertEquals("runnable must not be null", runnableException.getMessage());
        assertEquals("callable must not be null", callableException.getMessage());
        assertEquals("supplier must not be null", supplierException.getMessage());
        assertEquals("contextMap must not be null", runContextException.getMessage());
        assertEquals("runnable must not be null", runRunnableException.getMessage());
        assertEquals("contextMap must not be null", callContextException.getMessage());
        assertEquals("supplier must not be null", callSupplierException.getMessage());
    }

    private static Map<String, String> currentMdc() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap == null || contextMap.isEmpty() ? Map.of() : Map.copyOf(contextMap);
    }
}
