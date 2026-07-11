package io.github.coco.web.response;

/**
 * Coco Web 统一响应配置。
 * <p>
 * 维护正常响应和异常响应共同遵守的响应体规则，避免链路追踪、调试字段和业务响应结构耦合。
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
public class CocoResponseProperties {

    private CocoResponseMetadataMode metadataMode = CocoResponseMetadataMode.NONE;

    /**
     * <p>
     * 返回响应体元数据输出模式。
     * </p>
     * @return 响应体元数据输出模式
     */
    public CocoResponseMetadataMode getMetadataMode() {
        return this.metadataMode;
    }

    /**
     * <p>
     * 设置响应体元数据输出模式。
     * </p>
     * @param metadataMode 响应体元数据输出模式
     */
    public void setMetadataMode(CocoResponseMetadataMode metadataMode) {
        this.metadataMode = metadataMode == null ? CocoResponseMetadataMode.NONE : metadataMode;
    }
}
