package io.github.coco.spring.boot.jackson;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Jackson 配置属性。
 * <p>
 * 绑定 {@code coco.jackson} 命名空间，控制 Jackson 序列化与反序列化行为。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "coco.jackson")
public class CocoJacksonProperties {

    private boolean longToString = true;

    private boolean failOnUnknownProperties = false;

    private boolean writeDatesAsTimestamps = false;

    /**
     * <p>
     * 返回是否将 {@code Long} 类型序列化为字符串。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isLongToString() {
        return this.longToString;
    }

    /**
     * <p>
     * 设置是否将 {@code Long} 类型序列化为字符串。
     * </p>
     * @param longToString 是否启用
     */
    public void setLongToString(boolean longToString) {
        this.longToString = longToString;
    }

    /**
     * <p>
     * 返回反序列化时遇到未知属性是否抛出异常。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isFailOnUnknownProperties() {
        return this.failOnUnknownProperties;
    }

    /**
     * <p>
     * 设置反序列化时遇到未知属性是否抛出异常。
     * </p>
     * @param failOnUnknownProperties 是否抛出异常
     */
    public void setFailOnUnknownProperties(boolean failOnUnknownProperties) {
        this.failOnUnknownProperties = failOnUnknownProperties;
    }

    /**
     * <p>
     * 返回是否将日期类型序列化为时间戳。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isWriteDatesAsTimestamps() {
        return this.writeDatesAsTimestamps;
    }

    /**
     * <p>
     * 设置是否将日期类型序列化为时间戳。
     * </p>
     * @param writeDatesAsTimestamps 是否序列化为时间戳
     */
    public void setWriteDatesAsTimestamps(boolean writeDatesAsTimestamps) {
        this.writeDatesAsTimestamps = writeDatesAsTimestamps;
    }
}
