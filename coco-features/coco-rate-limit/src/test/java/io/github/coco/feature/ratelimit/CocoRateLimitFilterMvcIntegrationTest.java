package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/**
 * Filter 与 MVC 注解后备交互测试。
 */
class CocoRateLimitFilterMvcIntegrationTest {

    @Test
    void pathFilterWinsAndPreventsMvcFromTakingTheSameQuotaTwice() throws Exception {
        CocoRateLimitRoute route = route("path-route");
        CocoRateLimitRequestHandler requestHandler = CocoRateLimitRequestHandlerTest.handler(
                Instant.parse("2026-07-15T00:00:00Z"), (snapshot, configuredRoute) ->
                        new CocoRateLimitKey(configuredRoute.getId(), "same-key"));
        CocoRateLimitFilter filter = new CocoRateLimitFilter(request -> Optional.of(route), requestHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean();

        filter.doFilter(request, response, (filterRequest, filterResponse) -> chainCalled.set(true));
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher(route), requestHandler);

        assertThat(chainCalled).isTrue();
        assertThat(interceptor.preHandle(request, response, handlerMethod())).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void mvcUsesMethodAnnotationAsFallbackWhenNoPathRouteMatches() throws Exception {
        CocoRateLimitRoute route = route("method-route");
        CocoRateLimitRequestHandler requestHandler = CocoRateLimitRequestHandlerTest.handler(
                Instant.parse("2026-07-15T00:00:00Z"), (snapshot, configuredRoute) ->
                        new CocoRateLimitKey(configuredRoute.getId(), "fallback-key"));
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher(route), requestHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unmatched");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod())).isTrue();
        assertThat(request.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE)).isEqualTo("method-route");
    }

    @Test
    void mvcPrefersMethodAnnotationOverClassAnnotation() throws Exception {
        CocoRateLimitRoute classRoute = route("class-route");
        CocoRateLimitRoute methodRoute = route("method-route");
        CocoRateLimitRequestHandler requestHandler = CocoRateLimitRequestHandlerTest.handler(
                Instant.parse("2026-07-15T00:00:00Z"), (snapshot, configuredRoute) ->
                        new CocoRateLimitKey(configuredRoute.getId(), "annotation-key"));
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(
                routeMatcher(classRoute, methodRoute), requestHandler);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unmatched");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), classAndMethodHandlerMethod())).isTrue();
        assertThat(request.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE)).isEqualTo("method-route");
    }

    private static CocoRateLimitRouteMatcher routeMatcher(CocoRateLimitRoute... routes) {
        return new CocoRateLimitRouteMatcher() {
            @Override
            public Optional<CocoRateLimitRoute> resolve(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }

            @Override
            public Optional<CocoRateLimitRoute> resolve(String routeId) {
                for (CocoRateLimitRoute route : routes) {
                    if (route.getId().equals(routeId)) {
                        return Optional.of(route);
                    }
                }
                return Optional.empty();
            }
        };
    }

    private static CocoRateLimitRoute route(String id) {
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId(id);
        route.setLimit(1);
        route.setWindowSeconds(60);
        return route;
    }

    private static HandlerMethod handlerMethod() throws NoSuchMethodException {
        Method method = AnnotatedHandler.class.getDeclaredMethod("handle");
        return new HandlerMethod(new AnnotatedHandler(), method);
    }

    private static HandlerMethod classAndMethodHandlerMethod() throws NoSuchMethodException {
        Method method = ClassAndMethodAnnotatedHandler.class.getDeclaredMethod("handle");
        return new HandlerMethod(new ClassAndMethodAnnotatedHandler(), method);
    }

    static class AnnotatedHandler {

        @CocoRateLimited("method-route")
        void handle() {
        }
    }

    @CocoRateLimited("class-route")
    static class ClassAndMethodAnnotatedHandler {

        @CocoRateLimited("method-route")
        void handle() {
        }
    }
}
