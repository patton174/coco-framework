package io.github.coco.feature.codegen.crud;

/**
 * Coco CRUD 主键生成策略。
 * <p>
 * 枚举值与 MyBatis-Plus {@code IdType} 的稳定取值保持一致，生成模板可直接输出对应名称。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoCrudIdStrategy {

    /** 使用数据库自增主键。 */
    AUTO,

    /** 使用 MyBatis-Plus 全局默认主键策略。 */
    NONE,

    /** 由调用方显式输入主键。 */
    INPUT,

    /** 使用 MyBatis-Plus 分配的数值或字符串主键。 */
    ASSIGN_ID,

    /** 使用 MyBatis-Plus 分配的 UUID 主键。 */
    ASSIGN_UUID
}
