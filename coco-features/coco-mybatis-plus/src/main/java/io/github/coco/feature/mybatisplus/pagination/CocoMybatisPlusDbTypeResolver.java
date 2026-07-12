package io.github.coco.feature.mybatisplus.pagination;

import java.util.Locale;

import com.baomidou.mybatisplus.annotation.DbType;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;

/**
 * Coco MyBatis-Plus 数据库类型解析器。
 * <p>
 * 将业务配置中的宽松数据库类型文本转换为 MyBatis-Plus {@link DbType}，为空时保留自动推断语义。
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
public final class CocoMybatisPlusDbTypeResolver {

    private CocoMybatisPlusDbTypeResolver() {
    }

    /**
     * <p>
     * 解析数据库类型。
     * </p>
     * @param value 数据库类型配置文本
     * @return 数据库类型；配置为空时返回 {@code null}
     */
    public static DbType resolve(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmedValue = value.trim();
        DbType configuredDbType = DbType.getDbType(trimmedValue);
        if (configuredDbType != DbType.OTHER || isOther(trimmedValue)) {
            return configuredDbType;
        }
        String enumName = trimmedValue.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return DbType.valueOf(enumName);
        }
        catch (IllegalArgumentException ex) {
            throw CocoMybatisPlusErrorCode.INVALID_DB_TYPE.request(ex, trimmedValue);
        }
    }

    private static boolean isOther(String value) {
        return "other".equalsIgnoreCase(value) || "OTHER".equals(value.replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT));
    }
}
