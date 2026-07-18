package io.github.coco.feature.datapermission.sql;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Coco 数据权限 SQL 接入配置。
 * <p>
 * 描述数据权限模块是否接入 SQL 拦截、缺省处理策略以及业务资源到数据表的基础映射。
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
public class CocoDataPermissionSqlProperties {

    private boolean enabled;

    private CocoDataPermissionMissingContextPolicy missingContextPolicy =
            CocoDataPermissionMissingContextPolicy.THROW;

    private CocoDataPermissionMissingRulePolicy missingRulePolicy =
            CocoDataPermissionMissingRulePolicy.DENY;

    private Map<String, CocoDataPermissionSqlResourceProperties> resources = new LinkedHashMap<>();

    /**
     * <p>
     * 判断是否启用数据权限 SQL 拦截。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用数据权限 SQL 拦截。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回缺少数据权限上下文时的处理策略。
     * </p>
     * @return 缺少上下文策略
     */
    public CocoDataPermissionMissingContextPolicy getMissingContextPolicy() {
        return this.missingContextPolicy;
    }

    /**
     * <p>
     * 设置缺少数据权限上下文时的处理策略。
     * </p>
     * @param missingContextPolicy 缺少上下文策略
     */
    public void setMissingContextPolicy(CocoDataPermissionMissingContextPolicy missingContextPolicy) {
        this.missingContextPolicy = missingContextPolicy == null
                ? CocoDataPermissionMissingContextPolicy.THROW
                : missingContextPolicy;
    }

    /**
     * <p>
     * 返回缺少资源规则时的处理策略。
     * </p>
     * @return 缺少规则策略
     */
    public CocoDataPermissionMissingRulePolicy getMissingRulePolicy() {
        return this.missingRulePolicy;
    }

    /**
     * <p>
     * 设置缺少资源规则时的处理策略。
     * </p>
     * @param missingRulePolicy 缺少规则策略
     */
    public void setMissingRulePolicy(CocoDataPermissionMissingRulePolicy missingRulePolicy) {
        this.missingRulePolicy = missingRulePolicy == null
                ? CocoDataPermissionMissingRulePolicy.DENY
                : missingRulePolicy;
    }

    /**
     * <p>
     * 返回业务资源到表的映射配置。
     * </p>
     * @return 业务资源映射配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP",
            justification = "Existing configuration consumers use the live resource map for chained put operations.")
    public Map<String, CocoDataPermissionSqlResourceProperties> getResources() {
        return this.resources;
    }

    /**
     * <p>
     * 设置业务资源到表的映射配置。
     * </p>
     * @param resources 业务资源映射配置
     */
    public void setResources(Map<String, CocoDataPermissionSqlResourceProperties> resources) {
        this.resources = resources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resources);
    }

    /**
     * <p>
     * 根据资源标识返回资源 SQL 配置。
     * </p>
     * @param resource 资源标识
     * @return 资源 SQL 配置；不存在时返回空配置
     */
    public CocoDataPermissionSqlResourceProperties resource(String resource) {
        CocoDataPermissionSqlResourceProperties properties = this.resources.get(resource);
        return properties == null ? new CocoDataPermissionSqlResourceProperties() : properties;
    }

    /**
     * <p>
     * 创建供框架内部长期持有的独立 SQL 配置快照。
     * </p>
     * <p>
     * 配置 Bean 的公开 getter 保持 Spring Binder 和既有业务代码所依赖的 live mutable 语义；
     * 拦截器、解析器等内部消费者必须显式调用本方法，避免运行期配置对象被外部修改后改变已创建组件的行为。
     * </p>
     * @return 深复制的 SQL 配置快照
     */
    public CocoDataPermissionSqlProperties snapshot() {
        return snapshotOf(this);
    }

    static CocoDataPermissionSqlProperties snapshotOf(CocoDataPermissionSqlProperties source) {
        CocoDataPermissionSqlProperties copy = new CocoDataPermissionSqlProperties();
        if (source == null) {
            return copy;
        }
        copy.setEnabled(source.isEnabled());
        copy.setMissingContextPolicy(source.getMissingContextPolicy());
        copy.setMissingRulePolicy(source.getMissingRulePolicy());
        Map<String, CocoDataPermissionSqlResourceProperties> resourceCopies = new LinkedHashMap<>();
        source.resources.forEach((resource, properties) -> resourceCopies.put(resource,
                CocoDataPermissionSqlResourceProperties.snapshotOf(properties)));
        copy.setResources(resourceCopies);
        return copy;
    }
}
