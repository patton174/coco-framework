package io.github.coco.feature.web.response;

/**
 * Coco Web 统一响应模型。
 * <p>
 * 用于承载 Web 层返回给调用方的稳定响应结构，当前阶段先服务异常响应，后续普通响应包装也会复用该模型。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param success 请求是否成功
 * @param code 响应编码
 * @param message 响应消息
 * @param data 响应数据
 * @param traceId 请求链路标识
 * @param path 请求路径
 * @param <T> 响应数据类型
 * @author patton174
 * @since 1.0.0
 */
public record CocoApiResponse<T>(boolean success, int code, String message, T data, String traceId, String path) {

    /**
     * <p>
     * 创建统一响应对象，并校验响应编码。
     * </p>
     * @param success 请求是否成功
     * @param code 响应码
     * @param message 响应消息
     * @param data 响应数据
     * @param traceId 请求链路标识
     * @param path 请求路径
     */
    public CocoApiResponse {
        message = message == null ? "" : message;
        traceId = blankToNull(traceId);
        path = blankToNull(path);
    }

    /**
     * <p>
     * 创建异常响应对象。
     * </p>
     * @param code 异常响应码
     * @param message 异常消息
     * @param traceId 请求链路标识
     * @param path 请求路径
     * @param <T> 响应数据类型
     * @return 统一异常响应
     */
    public static <T> CocoApiResponse<T> error(int code, String message, String traceId, String path) {
        return new CocoApiResponse<>(false, code, message, null, traceId, path);
    }

    /**
     * <p>
     * 创建成功响应对象。
     * </p>
     * @param code 成功响应码
     * @param message 成功响应消息
     * @param data 响应数据
     * @param traceId 请求链路标识
     * @param path 请求路径
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> CocoApiResponse<T> success(int code, String message, T data, String traceId, String path) {
        return new CocoApiResponse<>(true, code, message, data, traceId, path);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
