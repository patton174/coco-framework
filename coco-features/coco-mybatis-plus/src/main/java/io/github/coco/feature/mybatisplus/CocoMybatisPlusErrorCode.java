package io.github.coco.feature.mybatisplus;

import io.github.coco.exception.CocoErrorCode;

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
    INVALID_DB_TYPE("coco.feature.mybatis-plus.error.invalid-db-type"),

    /**
     * 数据源路由键缺失、空白或超出最大长度限制。
     */
    INVALID_DATASOURCE_KEY("coco.feature.mybatis-plus.error.invalid-datasource-key"),

    /**
     * 数据源定义缺少必要的连接配置。
     */
    INVALID_DATASOURCE_DEFINITION("coco.feature.mybatis-plus.error.invalid-datasource-definition"),

    /**
     * 多数据源未注册所要求的主数据源。
     */
    PRIMARY_DATASOURCE_MISSING("coco.feature.mybatis-plus.error.primary-datasource-missing"),

    /**
     * 主数据源不允许在运行期被移除。
     */
    PRIMARY_DATASOURCE_UNREMOVABLE("coco.feature.mybatis-plus.error.primary-datasource-unremovable"),

    /**
     * 请求的目标数据源或分组未注册。
     */
    DATASOURCE_NOT_FOUND("coco.feature.mybatis-plus.error.datasource-not-found"),

    /**
     * 已激活的事务内不允许切换数据源。
     */
    DATASOURCE_TRANSACTION_CONFLICT("coco.feature.mybatis-plus.error.datasource-transaction-conflict");

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
