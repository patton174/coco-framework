package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import io.github.coco.feature.web.context.CocoWebRequestMatchRule;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 限流路由与注解意图测试。
 */
class DefaultCocoRateLimitRouteMatcherTest {

    @Test
    void usesTheFirstMatchingRouteAsTheExplicitPriority() {
        DefaultCocoRateLimitRouteMatcher matcher = matcher(route("broad", "/api/**"), route("specific", "/api/orders"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

        assertThat(matcher.resolve(request)).map(CocoRateLimitRoute::getId).contains("broad");
    }

    @Test
    void resolvesConfiguredRouteByAnnotationIdentifier() {
        DefaultCocoRateLimitRouteMatcher matcher = matcher(route("orders", "/api/orders"));

        assertThat(matcher.resolve("orders")).map(CocoRateLimitRoute::getId).contains("orders");
    }

    @Test
    void doesNotResolveBlankOrUnknownAnnotationIdentifiers() {
        DefaultCocoRateLimitRouteMatcher matcher = matcher(route("orders", "/api/orders"));

        assertThat(matcher.resolve(" ")).isEmpty();
        assertThat(matcher.resolve("missing")).isEmpty();
    }

    @Test
    void rejectsInvalidConfiguredRoutesAtConstructionTime() {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        properties.getRoutes().add(new CocoRateLimitRoute());

        assertThatThrownBy(() -> new DefaultCocoRateLimitRouteMatcher(properties,
                new DefaultCocoWebRequestMatcher())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supportsValueAliasAndLetsMethodIntentOverrideClassIntent() throws Exception {
        Method method = AnnotatedController.class.getDeclaredMethod("methodRoute");
        CocoRateLimited classIntent = AnnotatedElementUtils.findMergedAnnotation(AnnotatedController.class,
                CocoRateLimited.class);
        CocoRateLimited methodIntent = AnnotatedElementUtils.findMergedAnnotation(method, CocoRateLimited.class);

        assertThat(classIntent.route()).isEqualTo("class-route");
        assertThat(classIntent.value()).isEqualTo("class-route");
        assertThat(methodIntent.route()).isEqualTo("method-route");
    }

    private static DefaultCocoRateLimitRouteMatcher matcher(CocoRateLimitRoute... routes) {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        properties.getRoutes().addAll(List.of(routes));
        return new DefaultCocoRateLimitRouteMatcher(properties, new DefaultCocoWebRequestMatcher());
    }

    private static CocoRateLimitRoute route(String id, String pattern) {
        CocoWebRequestMatchRule matcher = new CocoWebRequestMatchRule();
        matcher.setMethods(Set.of("GET"));
        matcher.setPathPatterns(Set.of(pattern));
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId(id);
        route.setMatcher(matcher);
        route.setLimit(10);
        route.setWindowSeconds(60);
        return route;
    }

    @CocoRateLimited("class-route")
    static class AnnotatedController {

        @CocoRateLimited(route = "method-route")
        void methodRoute() {
        }
    }
}
