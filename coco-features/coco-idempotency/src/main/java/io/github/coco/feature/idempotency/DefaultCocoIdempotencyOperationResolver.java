package io.github.coco.feature.idempotency;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** 基于处理方法和完整 {@link RequestMapping} 条件的默认操作标识解析器。 */
public final class DefaultCocoIdempotencyOperationResolver implements CocoIdempotencyOperationResolver {
    @Override
    public String resolve(HttpServletRequest request, HandlerMethod handlerMethod) {
        Objects.requireNonNull(request, "request must not be null");
        HandlerMethod checked = Objects.requireNonNull(handlerMethod, "handlerMethod must not be null");
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String matchedPath = pattern == null ? request.getRequestURI() : String.valueOf(pattern);
        return checked.getBeanType().getName() + "#" + checked.getMethod().toGenericString()
                + "|class=" + mapping(checked.getBeanType())
                + "|method=" + mapping(checked.getMethod())
                + "|matched-path=" + matchedPath;
    }

    private static String mapping(AnnotatedElement element) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
        if (mapping == null) { return ""; }
        return "paths=" + values(mapping.path(), mapping.value())
                + ";methods=" + Arrays.stream(mapping.method()).map(RequestMethod::name).sorted()
                        .collect(Collectors.joining(","))
                + ";params=" + values(mapping.params())
                + ";headers=" + values(mapping.headers())
                + ";consumes=" + values(mapping.consumes())
                + ";produces=" + values(mapping.produces());
    }

    private static String values(String[]... groups) {
        return Arrays.stream(groups).flatMap(Arrays::stream).filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).map(value -> value.toLowerCase(Locale.ROOT)).distinct().sorted()
                .collect(Collectors.joining(","));
    }
}
