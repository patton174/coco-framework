package io.github.coco.feature.security.web;

import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.coco.context.CocoContextSnapshotRegistry;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CocoSecurityWebFilterAsyncIntegrationTest {

    private static final CocoSecurityContext SECURITY_CONTEXT = CocoSecurityContext.authenticated(
            CocoSecurityPrincipal.of("user-42", "Async User"));

    @AfterEach
    void clearSecurityContext() {
        CocoSecurityContextHolder.clear();
    }

    @Test
    void propagatesSecurityContextToAsyncCallableAndCleansDispatchThreads() throws Exception {
        CocoSecurityWebFilter filter = new CocoSecurityWebFilter(request -> Optional.of(SECURITY_CONTEXT));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AsyncSecurityController())
                .addFilters(filter)
                .build();

        MvcResult initialResult = mockMvc.perform(get("/async-security"))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertTrue(CocoSecurityContextHolder.current().isEmpty());

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("user-42", asyncResult.getResponse().getContentAsString());
        assertTrue(CocoSecurityContextHolder.current().isEmpty());
    }

    @Test
    void bindsAndClearsSecurityContextDuringErrorDispatch() throws Exception {
        CocoSecurityWebFilter filter = new CocoSecurityWebFilter(request -> Optional.of(SECURITY_CONTEXT));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/security-error");
        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/security-error");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
                assertEquals("user-42", CocoSecurityContextHolder.requireCurrent().principal().principalId()));

        assertTrue(CocoSecurityContextHolder.current().isEmpty());
    }

    @Test
    void servletAsyncCallbacksRestoreSecurityContextAndPreserveReusedWorkerState() throws Exception {
        for (AsyncCallback callback : AsyncCallback.values()) {
            CopyOnWriteArrayList<String> observedPrincipalIds = new CopyOnWriteArrayList<>();
            AsyncFixture fixture = startAsyncRequest(observedPrincipalIds);
            CocoSecurityContext workerContext = CocoSecurityContext.authenticated(
                    CocoSecurityPrincipal.of("worker", "Worker"));
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                String principalAfterCallback = worker.submit(() -> {
                    CocoSecurityContextHolder.set(workerContext);
                    callback.invoke(fixture.listener(), fixture.event());
                    return CocoSecurityContextHolder.requireCurrent().principal().principalId();
                }).get();

                assertEquals("worker", principalAfterCallback, callback.name());
                assertEquals(java.util.List.of("user-42"), observedPrincipalIds, callback.name());
                assertTrue(CocoSecurityContextHolder.current().isEmpty(), callback.name());
            }
            finally {
                worker.shutdownNow();
            }
        }
    }

    private static AsyncFixture startAsyncRequest(CopyOnWriteArrayList<String> observedPrincipalIds)
            throws Exception {
        CocoTraceProperties traceProperties = new CocoTraceProperties();
        CocoTraceFilter traceFilter = new CocoTraceFilter(traceProperties);
        CocoSecurityWebFilter securityFilter = new CocoSecurityWebFilter(request -> Optional.of(SECURITY_CONTEXT));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/async-listener-security");
        request.setAsyncSupported(true);
        request.addHeader(traceProperties.getHeaderName(), "0123456789abcdef0123456789abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                securityFilter.doFilter(traceRequest, traceResponse, (securityRequest, securityResponse) -> {
                    CocoContextSnapshotRegistry registry = (CocoContextSnapshotRegistry) securityRequest
                            .getAttribute(CocoContextSnapshotRegistry.class.getName());
                    registry.register(CocoSecurityWebFilterAsyncIntegrationTest.class.getName(), () -> {
                        observedPrincipalIds.add(CocoSecurityContextHolder.requireCurrent()
                                .principal().principalId());
                        return () -> {
                        };
                    });
                    securityRequest.startAsync(securityRequest, securityResponse);
                }));

        MockAsyncContext asyncContext = (MockAsyncContext) request.getAsyncContext();
        assertEquals(1, asyncContext.getListeners().size());
        return new AsyncFixture(asyncContext.getListeners().get(0),
                new AsyncEvent(asyncContext, request, response));
    }

    private enum AsyncCallback {
        COMPLETE {
            @Override
            void invoke(AsyncListener listener, AsyncEvent event) throws java.io.IOException {
                listener.onComplete(event);
            }
        },
        ERROR {
            @Override
            void invoke(AsyncListener listener, AsyncEvent event) throws java.io.IOException {
                listener.onError(new AsyncEvent(event.getAsyncContext(), event.getSuppliedRequest(),
                        event.getSuppliedResponse(), new IllegalStateException("async failure")));
            }
        },
        TIMEOUT {
            @Override
            void invoke(AsyncListener listener, AsyncEvent event) throws java.io.IOException {
                listener.onTimeout(event);
            }
        },
        START_ASYNC {
            @Override
            void invoke(AsyncListener listener, AsyncEvent event) throws java.io.IOException {
                listener.onStartAsync(event);
            }
        };

        abstract void invoke(AsyncListener listener, AsyncEvent event) throws java.io.IOException;
    }

    private record AsyncFixture(AsyncListener listener, AsyncEvent event) {
    }

    @RestController
    private static final class AsyncSecurityController {

        @GetMapping("/async-security")
        Callable<String> asyncSecurity() {
            return () -> CocoSecurityContextHolder.current()
                    .map(CocoSecurityContext::principal)
                    .map(CocoSecurityPrincipal::principalId)
                    .orElse("missing");
        }
    }
}
