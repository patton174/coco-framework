package io.github.coco.feature.mybatisplus.datasource;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 {@link ThreadLocalRandom} 的随机数据源负载均衡器。
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
public final class RandomCocoDataSourceLoadBalancer implements CocoDataSourceLoadBalancer {

    /**
     * {@inheritDoc}
     */
    @Override
    public String choose(String group, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw CocoMybatisPlusErrorCode.DATASOURCE_NOT_FOUND.system();
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index);
    }
}
