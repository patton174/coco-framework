package io.github.coco.feature.httpclient;

import org.springframework.web.client.RestClient;

/**
 * Coco 命名 HTTP 客户端定制器。
 * <p>
 * 实现 {@link org.springframework.core.Ordered} 或使用 {@link org.springframework.core.annotation.Order}
 * 可确定多个定制器的执行顺序。
 * </p>
 */
@FunctionalInterface
public interface CocoHttpClientCustomizer {

    /**
     * 判断是否定制指定客户端。
     * <p>
     * 默认匹配全部客户端；业务实现可按名称返回 {@code false} 排除不需要的客户端。
     * </p>
     * @param name 客户端名称
     * @return 应定制时返回 {@code true}
     */
    default boolean supports(String name) {
        return true;
    }

    /**
     * 定制指定命名客户端的构建器。
     * @param name 客户端名称
     * @param builder 客户端构建器
     */
    void customize(String name, RestClient.Builder builder);
}
