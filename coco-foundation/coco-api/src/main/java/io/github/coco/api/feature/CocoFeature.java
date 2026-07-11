package io.github.coco.api.feature;

import java.util.Arrays;
import java.util.Optional;

/**
 * Coco 标准功能标识。
 * <p>
 * 定义框架内置能力的稳定枚举，避免业务配置中直接散落字符串。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api}</li>
 * </ul>
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

    /**
     * <p>
     * 返回功能在配置文件、构建清单和日志中的稳定标识。
     * </p>
     * @return 功能标识
     */
    public String id() {
        return this.id;
    }

    /**
     * <p>
     * 根据稳定标识查找 Coco 标准功能。
     * </p>
     * @param id 功能标识
     * @return 匹配到的功能；未匹配时返回空结果
     */
    public static Optional<CocoFeature> fromId(String id) {
        return Arrays.stream(values())
                .filter(feature -> feature.id.equals(id))
                .findFirst();
    }
}
