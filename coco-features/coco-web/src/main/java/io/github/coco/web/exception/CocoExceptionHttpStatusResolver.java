package io.github.coco.web.exception;

import io.github.coco.exception.CocoException;
import org.springframework.http.HttpStatusCode;

/**
 * Coco 异常 HTTP 状态解析器。
 * <p>
 * 业务项目可以提供同类型 Bean 覆盖默认策略，将不同异常编码映射到自己的 HTTP 状态。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoExceptionHttpStatusResolver {

    /**
     * <p>
     * 根据 Coco 异常解析 HTTP 状态。
     * </p>
     * @param exception Coco 异常
     * @return HTTP 状态
     */
    HttpStatusCode resolve(CocoException exception);
}
