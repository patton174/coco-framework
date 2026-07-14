package io.github.coco.logging.context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.coco.context.CocoContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CocoMdcContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void wrappedTaskRestoresWorkerMdcBeforeThreadReuse() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MDC.put("traceId", "request-trace");
            CocoContextSnapshot snapshot = CocoMdcContext.capture();
            MDC.clear();

            String propagated = executor.submit(snapshot.wrap(() -> MDC.get("traceId"))).get();
            String residual = executor.submit(() -> MDC.get("traceId")).get();

            assertEquals("request-trace", propagated);
            assertNull(residual);
        }
        finally {
            executor.shutdownNow();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        }
    }
}
