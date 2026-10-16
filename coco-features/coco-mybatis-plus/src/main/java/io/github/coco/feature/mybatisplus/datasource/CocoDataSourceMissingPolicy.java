package io.github.coco.feature.mybatisplus.datasource;

/**
 * 请求的数据源或分组不存在时的处理策略。
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoDataSourceMissingPolicy {

    /**
     * 立即抛出系统异常，拒绝静默路由到错误的数据源。
     */
    FAIL,

    /**
     * 记录告警日志并回退至主数据源。
     */
    FALLBACK_PRIMARY
}
