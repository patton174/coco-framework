package io.github.coco.api.feature;

import java.util.Arrays;
import java.util.Optional;

/**
 * Standard Coco feature identifiers.
 */
public enum CocoFeature {

    WEB("web"),
    MYBATIS_PLUS("mybatis-plus"),
    AUDIT("audit"),
    SECURITY("security"),
    TENANT("tenant"),
    DATA_PERMISSION("data-permission"),
    OPENAPI("openapi"),
    CODEGEN("codegen");

    private final String id;

    CocoFeature(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static Optional<CocoFeature> fromId(String id) {
        return Arrays.stream(values())
                .filter(feature -> feature.id.equals(id))
                .findFirst();
    }
}
