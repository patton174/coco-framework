package io.github.coco.feature.idempotency;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** 基于完整稳定 {@link RequestMapping} 条件的默认操作标识解析器。 */
public final class DefaultCocoIdempotencyOperationResolver implements CocoIdempotencyOperationResolver {
    @Override
    public String resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        Objects.requireNonNull(request, "request must not be null");
        HandlerMethod checked = Objects.requireNonNull(handlerMethod, "handlerMethod must not be null");
        String matchedPath = matchedPattern(request);
        return "path=" + matchedPath
                + ";methods=" + methods(checked)
                + ";params=" + conditions(checked, RequestMapping::params)
                + ";headers=" + conditions(checked, RequestMapping::headers)
                + ";consumes=" + conditions(checked, RequestMapping::consumes)
                + ";produces=" + conditions(checked, RequestMapping::produces)
                + ";version=" + versions(checked);
    }

    private static String matchedPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) {
            throw new IllegalStateException("No Spring matching route pattern is available");
        }
        String value = String.valueOf(pattern).trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Spring matching route pattern is blank");
        }
        return value;
    }

    private static String methods(HandlerMethod handlerMethod) {
        return mappings(handlerMethod).flatMap(mapping -> Arrays.stream(mapping.method())).map(RequestMethod::name)
                .distinct().sorted().collect(Collectors.joining(","));
    }

    private static String conditions(HandlerMethod handlerMethod, MappingValues values) {
        return mappings(handlerMethod).flatMap(mapping -> Arrays.stream(values.apply(mapping))).filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).distinct().sorted()
                .collect(Collectors.joining(","));
    }

    private static String versions(HandlerMethod handlerMethod) {
        String methodVersion = version(AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                RequestMapping.class));
        if (!methodVersion.isEmpty()) {
            return methodVersion;
        }
        return version(AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequestMapping.class));
    }

    private static String version(RequestMapping mapping) {
        if (mapping == null || mapping.version() == null) {
            return "";
        }
        String version = mapping.version().trim();
        return version.isEmpty() ? "" : version;
    }

    private static java.util.stream.Stream<RequestMapping> mappings(HandlerMethod handlerMethod) {
        return java.util.stream.Stream.of(handlerMethod.getBeanType(), handlerMethod.getMethod())
                .map(element -> AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class))
                .filter(Objects::nonNull);
    }

    @FunctionalInterface
    private interface MappingValues { String[] apply(RequestMapping mapping); }
}
