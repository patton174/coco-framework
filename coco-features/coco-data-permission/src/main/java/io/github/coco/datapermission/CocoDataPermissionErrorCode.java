package io.github.coco.datapermission;

import io.github.coco.exception.CocoErrorCode;

/**
 * Coco 数据权限功能错误码。
 * <p>
 * 仅维护数据权限模块自身的框架级错误编码，具体提示文本由数据权限模块消息资源负责解析。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoDataPermissionErrorCode implements CocoErrorCode {

    /**
     * 当前线程不存在数据权限上下文。
     */
    CONTEXT_MISSING("coco.feature.data-permission.error.context-missing");

    private final String code;

    CocoDataPermissionErrorCode(String code) {
        this.code = code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String code() {
        return this.code;
    }
}
