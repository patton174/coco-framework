package io.github.coco.api.feature;

import java.util.Arrays;
import java.util.Optional;

/**
 * # Coco 标准功能标识
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-api`
 *
 * 定义框架内置能力的稳定枚举，避免业务配置中直接散落字符串。
 *
 * @author patton174
 * @since 1.0.0
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
