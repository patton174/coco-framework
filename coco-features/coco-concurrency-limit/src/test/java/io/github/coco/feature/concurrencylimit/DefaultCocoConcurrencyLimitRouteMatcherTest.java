package io.github.coco.feature.concurrencylimit;

import java.util.List;
import java.util.Set;

import io.github.coco.feature.web.context.CocoWebRequestMatchRule;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultCocoConcurrencyLimitRouteMatcherTest {

    @Test
    void resolvesLowestOrderBeforeDeclarationOrder() {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.setRoutes(List.of(
                route("general", 100, "/api/**"),
                route("specific", 10, "/api/orders")));
        DefaultCocoConcurrencyLimitRouteMatcher matcher = matcher(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");

        assertThat(matcher.resolve(request)).get().extracting(CocoConcurrencyLimitRoute::getId)
                .isEqualTo("specific");
    }

    @Test
    void keepsDeclarationOrderForEqualOrder() {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.setRoutes(List.of(
                route("first", 10, "/api/**"),
                route("second", 10, "/api/orders")));
        DefaultCocoConcurrencyLimitRouteMatcher matcher = matcher(properties);

        assertThat(matcher.resolve(new MockHttpServletRequest("GET", "/api/orders")))
                .get().extracting(CocoConcurrencyLimitRoute::getId).isEqualTo("first");
    }

    @Test
    void annotationOnlyRouteResolvesByIdButNeverByPath() {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        CocoConcurrencyLimitRoute annotationOnly = route("annotation", 0, null);
        properties.setRoutes(List.of(annotationOnly));
        DefaultCocoConcurrencyLimitRouteMatcher matcher = matcher(properties);

        assertThat(matcher.resolve("annotation")).containsSame(annotationOnly);
        assertThat(matcher.resolve(new MockHttpServletRequest("GET", "/anything"))).isEmpty();
    }

    @Test
    void rejectsDuplicateRouteIds() {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.setRoutes(List.of(route("duplicate", 0, "/one"), route("duplicate", 1, "/two")));

        assertThatThrownBy(() -> matcher(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private static DefaultCocoConcurrencyLimitRouteMatcher matcher(CocoConcurrencyLimitProperties properties) {
        return new DefaultCocoConcurrencyLimitRouteMatcher(properties, new DefaultCocoWebRequestMatcher());
    }

    private static CocoConcurrencyLimitRoute route(String id, int order, String path) {
        CocoConcurrencyLimitRoute route = new CocoConcurrencyLimitRoute();
        route.setId(id);
        route.setOrder(order);
        route.setLimit(1);
        if (path != null) {
            CocoWebRequestMatchRule matcher = new CocoWebRequestMatchRule();
            matcher.setPathPatterns(Set.of(path));
            route.setMatcher(matcher);
        }
        return route;
    }
}
