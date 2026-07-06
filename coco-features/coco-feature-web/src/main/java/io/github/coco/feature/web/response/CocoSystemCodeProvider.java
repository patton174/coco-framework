package io.github.coco.feature.web.response;

/**
 * Coco Web 系统响应码提供器。
 * <p>
 * 当业务异常未显式指定业务码时，Web 统一响应会从该提供器读取系统默认响应码；业务系统可以通过注册自定义
 * {@code CocoSystemCodeProvider} Bean 覆盖这些默认码。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoSystemCodeProvider {

    /**
     * <p>
     * 返回成功响应码。
     * </p>
     * @return 成功响应码
     */
    int success();

    /**
     * <p>
     * 返回请求参数错误响应码。
     * </p>
     * @return 请求参数错误响应码
     */
    int invalidArgument();

    /**
     * <p>
     * 返回未认证响应码。
     * </p>
     * @return 未认证响应码
     */
    int unauthorized();

    /**
     * <p>
     * 返回无权限响应码。
     * </p>
     * @return 无权限响应码
     */
    int forbidden();

    /**
     * <p>
     * 返回资源不存在响应码。
     * </p>
     * @return 资源不存在响应码
     */
    int notFound();

    /**
     * <p>
     * 返回资源冲突响应码。
     * </p>
     * @return 资源冲突响应码
     */
    int conflict();

    /**
     * <p>
     * 返回系统内部错误响应码。
     * </p>
     * @return 系统内部错误响应码
     */
    int internalError();
}
