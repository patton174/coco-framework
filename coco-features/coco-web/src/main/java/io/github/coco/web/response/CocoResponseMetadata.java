package io.github.coco.web.response;

/**
 * Coco Web 响应元数据。
 * <p>
 * 根据 {@link CocoResponseMetadataMode} 裁剪可写入统一响应体的链路字段，避免响应封装层直接暴露完整请求上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param traceId 请求链路标识
 * @param path 请求路径
 * @author patton174
 * @since 1.0.0
 */
public record CocoResponseMetadata(String traceId, String path) {

    private static final CocoResponseMetadata EMPTY = new CocoResponseMetadata(null, null);

    /**
     * <p>
     * 创建响应元数据，并将空白字段归一为空值。
     * </p>
     * @param traceId 请求链路标识
     * @param path 请求路径
     */
    public CocoResponseMetadata {
        traceId = blankToNull(traceId);
        path = blankToNull(path);
    }

    /**
     * <p>
     * 返回空响应元数据。
     * </p>
     * @return 空响应元数据
     */
    public static CocoResponseMetadata empty() {
        return EMPTY;
    }

    /**
     * <p>
     * 按响应配置裁剪链路字段。
     * </p>
     * @param properties 响应配置属性
     * @param traceId 请求链路标识
     * @param path 请求路径
     * @return 裁剪后的响应元数据
     */
    public static CocoResponseMetadata from(CocoResponseProperties properties, String traceId, String path) {
        CocoResponseMetadataMode mode = properties == null
                ? CocoResponseMetadataMode.NONE
                : properties.getMetadataMode();
        if (mode == CocoResponseMetadataMode.NONE || mode == CocoResponseMetadataMode.COOKIE) {
            return empty();
        }
        return new CocoResponseMetadata(mode.includesTraceId() ? traceId : null,
                mode.includesPath() ? path : null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
