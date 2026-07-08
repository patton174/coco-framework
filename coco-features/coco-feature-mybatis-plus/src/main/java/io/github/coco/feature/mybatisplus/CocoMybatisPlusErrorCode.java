package io.github.coco.feature.mybatisplus;

import io.github.coco.common.exception.CocoErrorCode;

/**
 * Coco MyBatis-Plus 功能错误码。
 * <p>
 * 仅维护 MyBatis-Plus 模块自身的框架级错误编码，具体提示文本由 MyBatis-Plus 模块消息资源负责解析。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoMybatisPlusErrorCode implements CocoErrorCode {

    /**
     * 数据库类型配置无法映射到 MyBatis-Plus 支持的数据库类型。
     */
    INVALID_DB_TYPE("coco.feature.mybatis-plus.error.invalid-db-type");

    private final String code;

    CocoMybatisPlusErrorCode(String code) {
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
