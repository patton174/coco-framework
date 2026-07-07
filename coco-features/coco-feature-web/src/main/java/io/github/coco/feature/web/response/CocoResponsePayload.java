package io.github.coco.feature.web.response;

/**
 * Coco Web 响应语义负载。
 * <p>
 * 表示框架已经解析完成的响应语义，包括成功标识、响应码、消息、数据和可选元数据。具体输出成什么 Java
 * 对象由 {@link CocoResponseBodyFactory} 决定。
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
 * @param code 响应码
 * @param message 响应消息
 * @param data 响应数据
 * @param metadata 响应元数据
 * @param <T> 响应数据类型
 * @author patton174
 * @since 1.0.0
 */
public record CocoResponsePayload<T>(boolean success, int code, String message, T data,
        CocoResponseMetadata metadata) {

    /**
     * <p>
     * 创建响应语义负载，并归一化空消息和空元数据。
     * </p>
     * @param success 请求是否成功
     * @param code 响应码
     * @param message 响应消息
     * @param data 响应数据
     * @param metadata 响应元数据
     */
    public CocoResponsePayload {
        message = message == null ? "" : message;
        metadata = metadata == null ? CocoResponseMetadata.empty() : metadata;
    }

    /**
     * <p>
     * 创建成功响应语义负载。
     * </p>
     * @param code 成功响应码
     * @param message 成功响应消息
     * @param data 响应数据
     * @param metadata 响应元数据
     * @param <T> 响应数据类型
     * @return 成功响应语义负载
     */
    public static <T> CocoResponsePayload<T> success(int code, String message, T data,
            CocoResponseMetadata metadata) {
        return new CocoResponsePayload<>(true, code, message, data, metadata);
    }

    /**
     * <p>
     * 创建异常响应语义负载。
     * </p>
     * @param code 异常响应码
     * @param message 异常响应消息
     * @param metadata 响应元数据
     * @return 异常响应语义负载
     */
    public static CocoResponsePayload<Void> error(int code, String message, CocoResponseMetadata metadata) {
        return new CocoResponsePayload<>(false, code, message, null, metadata);
    }
}
