package io.github.coco.feature.security.web;

import java.util.Optional;
import java.util.concurrent.Callable;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
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
