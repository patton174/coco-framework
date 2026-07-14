package io.github.coco.feature.web.trace;

import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CocoTraceFilterAsyncIntegrationTest {

    private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void clearThreadContext() {
        CocoRequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void propagatesTraceRequestContextAndMdcToAsyncCallable() throws Exception {
        CocoTraceProperties properties = new CocoTraceProperties();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AsyncContextController(properties.getMdcKey()))
                .addFilters(new CocoTraceFilter(properties))
                .build();

        MvcResult initialResult = mockMvc.perform(get("/async-context")
                        .header(properties.getHeaderName(), TRACE_ID))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = new ObjectMapper().readTree(asyncResult.getResponse().getContentAsByteArray());

        assertEquals(TRACE_ID, body.path("traceId").asText());
        assertEquals(TRACE_ID, body.path("requestTraceId").asText());
        assertEquals(TRACE_ID, body.path("mdcTraceId").asText());
    }

    @Test
    void recordsFinalAsyncStatusAndDurationExactlyOnce() throws Exception {
        CocoTraceProperties properties = new CocoTraceProperties();
        CocoAccessLogCaptureProperties accessLogProperties = new CocoAccessLogCaptureProperties();
        CopyOnWriteArrayList<CocoAccessLog> accessLogs = new CopyOnWriteArrayList<>();
        CocoAccessLogRecorder recorder = accessLogs::add;
        CountDownLatch callableStarted = new CountDownLatch(1);
        CountDownLatch releaseCallable = new CountDownLatch(1);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new FinalResponseController(callableStarted, releaseCallable))
                .addFilters(new CocoTraceFilter(properties, java.util.List.of(recorder), accessLogProperties))
                .build();

        MvcResult initialResult = mockMvc.perform(get("/async-final-status")
                        .header(properties.getHeaderName(), TRACE_ID))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertTrue(callableStarted.await(5L, TimeUnit.SECONDS));
        Thread.sleep(75L);
        releaseCallable.countDown();

        mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isAccepted());

        assertEquals(1, accessLogs.size());
        assertEquals(202, accessLogs.get(0).status());
        assertTrue(accessLogs.get(0).durationMillis() >= 50L);
    }

    @Test
    void bindsAndClearsTraceContextDuringErrorDispatch() throws Exception {
        CocoTraceProperties properties = new CocoTraceProperties();
        CocoTraceFilter filter = new CocoTraceFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error-dispatch");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/error-dispatch");
        request.addHeader(properties.getHeaderName(), TRACE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertEquals(TRACE_ID, CocoTraceContext.currentTraceId().orElse(null));
            assertEquals(TRACE_ID, CocoRequestContextHolder.current()
                    .map(CocoRequestContext::traceId)
                    .orElse(null));
            assertEquals(TRACE_ID, MDC.get(properties.getMdcKey()));
        });

        assertTrue(CocoTraceContext.currentTraceId().isEmpty());
        assertTrue(CocoRequestContextHolder.current().isEmpty());
        assertNull(MDC.get(properties.getMdcKey()));
    }

    @RestController
    private static final class AsyncContextController {

        private final String mdcKey;

        private AsyncContextController(String mdcKey) {
            this.mdcKey = mdcKey;
        }

        @GetMapping("/async-context")
        Callable<AsyncContextView> asyncContext() {
            return () -> new AsyncContextView(
                    CocoTraceContext.currentTraceId().orElse(null),
                    CocoRequestContextHolder.current().map(CocoRequestContext::traceId).orElse(null),
                    MDC.get(this.mdcKey));
        }
    }

    @RestController
    private static final class FinalResponseController {

        private final CountDownLatch callableStarted;

        private final CountDownLatch releaseCallable;

        private FinalResponseController(CountDownLatch callableStarted, CountDownLatch releaseCallable) {
            this.callableStarted = callableStarted;
            this.releaseCallable = releaseCallable;
        }

        @GetMapping("/async-final-status")
        Callable<ResponseEntity<String>> finalStatus() {
            return () -> {
                this.callableStarted.countDown();
                if (!this.releaseCallable.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("async callable was not released");
                }
                return ResponseEntity.accepted().body("accepted");
            };
        }
    }

    private record AsyncContextView(String traceId, String requestTraceId, String mdcTraceId) {
    }
}
