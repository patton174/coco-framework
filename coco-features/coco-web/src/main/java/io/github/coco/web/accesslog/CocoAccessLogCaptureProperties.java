package io.github.coco.web.accesslog;

/**
 * Coco Web 访问日志采集配置属性。
 * <p>
 * 绑定 {@code coco.web.access-log} 命名空间，控制 Web 入口是否发布访问日志事件。
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
public class CocoAccessLogCaptureProperties {

    private boolean enabled = true;

    /**
     * <p>
     * 返回是否发布接口访问日志事件。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否发布接口访问日志事件。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
