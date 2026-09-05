package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class CocoRateLimitMvcInterceptorTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    void methodAnnotationTakesPriorityOverClassAnnotation() throws Exception {
        RecordingRouteMatcher routeMatcher = new RecordingRouteMatcher();
        CocoRateLimitRoute methodRoute = route("method-route");
        routeMatcher.add(methodRoute);
        AtomicReference<String> acquiredRoute = new AtomicReference<>();
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher,
                requestHandler(acquiredRoute, new AtomicInteger()));
        MockHttpServletRequest request = request();

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(),
                new HandlerMethod(new ClassAnnotatedController(), ClassAnnotatedController.class
                        .getMethod("methodOverride")))).isTrue();

        assertThat(acquiredRoute).hasValue("method-route");
        assertThat(request.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE)).isEqualTo("method-route");
        assertThat(routeMatcher.resolvedRouteIds()).containsExactly("method-route");
    }

    @Test
    void unannotatedHandlerPassesThroughWithoutResolvingOrCharging() throws Exception {
        RecordingRouteMatcher routeMatcher = new RecordingRouteMatcher();
        AtomicInteger acquisitions = new AtomicInteger();
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher,
                requestHandler(new AtomicReference<>(), acquisitions));

        assertThat(interceptor.preHandle(request(), new MockHttpServletResponse(),
                new HandlerMethod(new UnannotatedController(), UnannotatedController.class.getMethod("handle"))))
                .isTrue();

        assertThat(acquisitions).hasValue(0);
        assertThat(routeMatcher.resolvedRouteIds()).isEmpty();
    }

    @Test
    void filterAppliedRouteSkipsMvcFallbackWithoutDoubleCharging() throws Exception {
        RecordingRouteMatcher routeMatcher = new RecordingRouteMatcher();
        AtomicInteger acquisitions = new AtomicInteger();
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher,
                requestHandler(new AtomicReference<>(), acquisitions));
        MockHttpServletRequest request = request();
        request.setAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE, "path-route");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(),
                new HandlerMethod(new ClassAnnotatedController(), ClassAnnotatedController.class
                        .getMethod("classFallback")))).isTrue();

        assertThat(acquisitions).hasValue(0);
        assertThat(routeMatcher.resolvedRouteIds()).isEmpty();
    }

    @Test
    void configuredAnnotationRouteCallsHandlerAndMarksRequestAfterAllowing() throws Exception {
        RecordingRouteMatcher routeMatcher = new RecordingRouteMatcher();
        CocoRateLimitRoute classRoute = route("class-route");
        routeMatcher.add(classRoute);
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicReference<String> acquiredRoute = new AtomicReference<>();
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher,
                requestHandler(acquiredRoute, acquisitions));
        MockHttpServletRequest request = request();

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(),
                new HandlerMethod(new ClassAnnotatedController(), ClassAnnotatedController.class
                        .getMethod("classFallback")))).isTrue();

        assertThat(acquisitions).hasValue(1);
        assertThat(acquiredRoute).hasValue("class-route");
        assertThat(request.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE)).isEqualTo("class-route");
        assertThat(routeMatcher.resolvedRouteIds()).containsExactly("class-route");
    }

    @Test
    void unknownAnnotationRouteFailsWithAnExplicitException() throws Exception {
        RecordingRouteMatcher routeMatcher = new RecordingRouteMatcher();
        AtomicInteger acquisitions = new AtomicInteger();
        CocoRateLimitMvcInterceptor interceptor = new CocoRateLimitMvcInterceptor(routeMatcher,
                requestHandler(new AtomicReference<>(), acquisitions));

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(),
                new HandlerMethod(new ClassAnnotatedController(), ClassAnnotatedController.class
                        .getMethod("methodOverride"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("@CocoRateLimited references an unknown route");

        assertThat(acquisitions).hasValue(0);
        assertThat(routeMatcher.resolvedRouteIds()).containsExactly("method-route");
    }

    private static CocoRateLimitRequestHandler requestHandler(AtomicReference<String> acquiredRoute,
            AtomicInteger acquisitions) {
        CocoRateLimitKeyResolver keyResolver = (request, route) -> {
            acquiredRoute.set(route.getId());
            return new CocoRateLimitKey(route.getId(), "mvc-test");
        };
        CocoRateLimitStore store = permit -> {
            acquisitions.incrementAndGet();
            return new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1,
                    Instant.EPOCH.plusSeconds(permit.windowSeconds()), false);
        };
        return new CocoRateLimitRequestHandler(keyResolver, store,
                new CocoRateLimitResponseWriter(new TestMessageService(), new ObjectMapper()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CocoRateLimitRoute route(String id) {
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId(id);
        route.setLimit(2);
        route.setWindowSeconds(60);
        route.getMatcher().setPathPatterns(java.util.List.of("/**"));
        return route;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/7");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    @CocoRateLimited(route = "class-route")
    private static final class ClassAnnotatedController {

        @CocoRateLimited(route = "method-route")
        public void methodOverride() {
        }

        public void classFallback() {
        }
    }

    private static final class UnannotatedController {

        public void handle() {
        }
    }

    private static final class TestMessageService implements CocoMessageService {

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }

        @Override
        public String getMessage(String code, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
            return defaultMessage;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return defaultMessage;
        }

        @Override
        public String resolve(CocoMessage message) {
            return message.code();
        }

        @Override
        public String resolve(CocoMessage message, Locale locale) {
            return message.code();
        }
    }

    private static final class RecordingRouteMatcher implements CocoRateLimitRouteMatcher {

        private final Map<String, CocoRateLimitRoute> routes = new HashMap<>();

        private final List<String> resolvedRouteIds = new ArrayList<>();

        void add(CocoRateLimitRoute route) {
            this.routes.put(route.getId(), route);
        }

        List<String> resolvedRouteIds() {
            return List.copyOf(this.resolvedRouteIds);
        }

        @Override
        public Optional<CocoRateLimitRoute> resolve(jakarta.servlet.http.HttpServletRequest request) {
            return Optional.empty();
        }

        @Override
        public Optional<CocoRateLimitRoute> resolve(String routeId) {
            this.resolvedRouteIds.add(routeId);
            return Optional.ofNullable(this.routes.get(routeId));
        }
    }
}
