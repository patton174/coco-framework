package io.github.coco.fixture;

import org.springframework.web.context.request.RequestContextHolder;

final class WebFeatureClasspathProbe {

    private WebFeatureClasspathProbe() {
    }

    static Class<?> springWebType() {
        return RequestContextHolder.class;
    }
}
