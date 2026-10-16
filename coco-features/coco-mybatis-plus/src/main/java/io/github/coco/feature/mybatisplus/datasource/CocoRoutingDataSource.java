package io.github.coco.feature.mybatisplus.datasource;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.StringUtils;

/**
 * Coco 动态多数据源路由实现。
 * <p>
 * 继承 Spring {@link AbstractRoutingDataSource} 并作为全局唯一 {@code @Primary DataSource} 暴露给 MyBatis-Plus，
 * 使单 {@code SqlSessionFactory} 与 Coco 统一拦截器链（分页、租户、数据权限、SQL 防护）在所有数据源上保持一致生效。
 * </p>
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
public final class CocoRoutingDataSource extends AbstractRoutingDataSource
        implements CocoDataSourceRegistry, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoRoutingDataSource.class);

    private final Object monitor = new Object();

    private final CocoMybatisPlusDataSourceProperties properties;

    private final CocoDataSourceLoadBalancer loadBalancer;

    private final CocoDataSourceRouter router;

    private final String primary;

    private final Map<String, DataSource> dataSources = new LinkedHashMap<>();

    private final Map<String, String> explicitGroups = new LinkedHashMap<>();

    private volatile Map<String, DataSource> dataSourceSnapshot = Map.of();

    private volatile Map<String, List<String>> groupSnapshot = Map.of();

    /**
     * <p>
     * 创建并初始化动态路由数据源。
     * </p>
     * @param properties 多数据源配置属性
     * @param providers 数据源提供者列表
     * @param loadBalancer 分组负载均衡器
     * @param router 当前路由键决策器
     */
    public CocoRoutingDataSource(
            CocoMybatisPlusDataSourceProperties properties,
            List<CocoDataSourceProvider> providers,
            CocoDataSourceLoadBalancer loadBalancer,
            CocoDataSourceRouter router) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.loadBalancer = Objects.requireNonNull(loadBalancer, "loadBalancer must not be null");
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.primary = requireValidKey(this.properties.getPrimary());
        try {
            loadInitialDataSources(providers == null ? List.of() : providers);
            if (!this.dataSources.containsKey(this.primary)) {
                throw CocoMybatisPlusErrorCode.PRIMARY_DATASOURCE_MISSING.system();
            }
            refreshSnapshotsAndRoutingTargets();
        }
        catch (RuntimeException ex) {
            closeAllSilently(this.dataSources.values());
            throw ex;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPrimary() {
        return this.primary;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerDataSource(String name, String group, DataSource dataSource) {
        String normalizedName = requireValidKey(name);
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        DataSource replaced;
        synchronized (this.monitor) {
            replaced = this.dataSources.put(normalizedName, dataSource);
            if (StringUtils.hasText(group)) {
                this.explicitGroups.put(normalizedName, group.trim());
            }
            else {
                this.explicitGroups.remove(normalizedName);
            }
            refreshSnapshotsAndRoutingTargets();
        }
        if (replaced != null && replaced != dataSource) {
            closeSilently(normalizedName, replaced);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unregisterDataSource(String name) {
        String normalizedName = requireValidKey(name);
        if (this.primary.equals(normalizedName)) {
            throw CocoMybatisPlusErrorCode.PRIMARY_DATASOURCE_UNREMOVABLE.conflict();
        }
        DataSource removed;
        synchronized (this.monitor) {
            removed = this.dataSources.remove(normalizedName);
            if (removed == null) {
                return false;
            }
            this.explicitGroups.remove(normalizedName);
            refreshSnapshotsAndRoutingTargets();
        }
        closeSilently(normalizedName, removed);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsDataSource(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        return this.dataSourceSnapshot.containsKey(name.trim());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsGroup(String group) {
        if (!StringUtils.hasText(group)) {
            return false;
        }
        List<String> candidates = this.groupSnapshot.get(group.trim());
        return candidates != null && !candidates.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<DataSource> getDataSource(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.dataSourceSnapshot.get(name.trim()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getDataSourceNames() {
        return this.dataSourceSnapshot.keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> getGroupDataSourceNames() {
        return this.groupSnapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveEffectiveDataSourceName(String requestedKey) {
        if (!StringUtils.hasText(requestedKey)) {
            return this.primary;
        }
        String normalizedKey = requestedKey.trim();
        Map<String, DataSource> currentSources = this.dataSourceSnapshot;
        if (currentSources.containsKey(normalizedKey)) {
            return normalizedKey;
        }
        List<String> groupCandidates = this.groupSnapshot.get(normalizedKey);
        if (groupCandidates != null && !groupCandidates.isEmpty()) {
            String selected = this.loadBalancer.choose(normalizedKey, groupCandidates);
            if (StringUtils.hasText(selected) && currentSources.containsKey(selected.trim())) {
                return selected.trim();
            }
        }
        if (this.properties.getMissingPolicy() == CocoDataSourceMissingPolicy.FALLBACK_PRIMARY) {
            LOGGER.warn(
                    "Coco MyBatis-Plus requested datasource '{}' is not registered; falling back to primary datasource '{}'.",
                    normalizedKey,
                    this.primary);
            return this.primary;
        }
        throw CocoMybatisPlusErrorCode.DATASOURCE_NOT_FOUND.system();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return this.router.currentDataSourceKey()
                .map(this::resolveEffectiveDataSourceName)
                .orElse(this.primary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataSource determineTargetDataSource() {
        String effectiveName = String.valueOf(determineCurrentLookupKey());
        DataSource target = this.dataSourceSnapshot.get(effectiveName);
        if (target != null) {
            return target;
        }
        throw CocoMybatisPlusErrorCode.DATASOURCE_NOT_FOUND.system();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() {
        synchronized (this.monitor) {
            if (!this.dataSources.containsKey(this.primary)) {
                throw CocoMybatisPlusErrorCode.PRIMARY_DATASOURCE_MISSING.system();
            }
            refreshSnapshotsAndRoutingTargets();
        }
    }

    /**
     * <p>
     * 关闭所有受管的物理数据源连接池。
     * </p>
     */
    @Override
    public void close() {
        List<Map.Entry<String, DataSource>> entries;
        synchronized (this.monitor) {
            entries = new ArrayList<>(this.dataSources.entrySet());
        }
        Set<DataSource> closedInstances = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Map.Entry<String, DataSource> entry : entries) {
            DataSource dataSource = entry.getValue();
            if (dataSource != null && closedInstances.add(dataSource)) {
                closeSilently(entry.getKey(), dataSource);
            }
        }
    }

    private void loadInitialDataSources(List<CocoDataSourceProvider> providers) {
        for (CocoDataSourceProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            Map<String, DataSource> loadedSources = provider.loadDataSources();
            Map<String, String> loadedGroups = provider.loadDataSourceGroups();
            if (loadedSources == null || loadedSources.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, DataSource> entry : loadedSources.entrySet()) {
                String normalizedName = requireValidKey(entry.getKey());
                DataSource dataSource = Objects.requireNonNull(entry.getValue(),
                        "loaded DataSource must not be null for key: " + normalizedName);
                DataSource replaced = this.dataSources.put(normalizedName, dataSource);
                if (replaced != null && replaced != dataSource) {
                    closeSilently(normalizedName, replaced);
                }
                if (loadedGroups != null) {
                    String explicitGroup = loadedGroups.get(entry.getKey());
                    if (!StringUtils.hasText(explicitGroup)) {
                        explicitGroup = loadedGroups.get(normalizedName);
                    }
                    if (StringUtils.hasText(explicitGroup)) {
                        this.explicitGroups.put(normalizedName, explicitGroup.trim());
                    }
                }
            }
        }
    }

    private void refreshSnapshotsAndRoutingTargets() {
        Map<String, DataSource> copiedSources = Collections.unmodifiableMap(new LinkedHashMap<>(this.dataSources));
        this.dataSourceSnapshot = copiedSources;
        this.groupSnapshot = buildGroupSnapshot(copiedSources.keySet());
        Map<Object, Object> targetMap = new LinkedHashMap<>(copiedSources.size());
        targetMap.putAll(copiedSources);
        super.setTargetDataSources(targetMap);
        super.setDefaultTargetDataSource(copiedSources.get(this.primary));
        super.setLenientFallback(this.properties.getMissingPolicy() == CocoDataSourceMissingPolicy.FALLBACK_PRIMARY);
        super.afterPropertiesSet();
    }

    private Map<String, List<String>> buildGroupSnapshot(Set<String> names) {
        Map<String, Set<String>> mutableGroups = new LinkedHashMap<>();
        String separator = this.properties.getGroupSeparator();
        for (String name : names) {
            String group = this.explicitGroups.get(name);
            if (!StringUtils.hasText(group) && StringUtils.hasText(separator)) {
                int index = name.indexOf(separator);
                if (index > 0) {
                    String prefix = name.substring(0, index).trim();
                    if (StringUtils.hasText(prefix)) {
                        group = prefix;
                    }
                }
            }
            if (StringUtils.hasText(group)) {
                mutableGroups.computeIfAbsent(group.trim(), ignored -> new LinkedHashSet<>()).add(name);
            }
        }
        if (mutableGroups.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> immutableGroups = new LinkedHashMap<>(mutableGroups.size());
        for (Map.Entry<String, Set<String>> entry : mutableGroups.entrySet()) {
            immutableGroups.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutableGroups);
    }

    private static String requireValidKey(String name) {
        if (name == null || name.isBlank()) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
        return name.trim();
    }

    private static void closeAllSilently(Iterable<DataSource> dataSources) {
        for (DataSource dataSource : dataSources) {
            if (dataSource != null) {
                closeSilently("uninitialized", dataSource);
            }
        }
    }

    private static void closeSilently(String name, DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            }
            catch (Exception ex) {
                LOGGER.warn("Failed to close Coco managed DataSource '{}'.", name, ex);
            }
        }
    }
}
