package io.github.coco.spring.boot.jackson;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco Jackson 自动配置。
 * <p>
 * 在 Spring Boot 的 {@link JacksonAutoConfiguration} 之前注册，通过
 * {@link JsonMapperBuilderCustomizer} 统一定制 Jackson 序列化行为：
 * 可选将 {@code Long} 类型序列化为字符串以避免前端精度丢失，
 * 并控制未知属性处理策略。Jackson 3.x 内置 {@code java.time} 支持，
 * 默认以 ISO 格式输出日期时间，无需额外配置。
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
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass(JsonMapper.class)
@EnableConfigurationProperties(CocoJacksonProperties.class)
public class CocoJacksonAutoConfiguration {

    /**
     * <p>
     * 创建 Coco Jackson 定制器。
     * </p>
     * @param properties Coco Jackson 配置属性
     * @return Jackson 定制器
     */
    @Bean
    public JsonMapperBuilderCustomizer cocoJacksonCustomizer(CocoJacksonProperties properties) {
        return builder -> {
            if (properties.isLongToString()) {
                SimpleModule longModule = new SimpleModule("CocoLongToString");
                longModule.addSerializer(Long.class, ToStringSerializer.instance);
                longModule.addSerializer(long.class, ToStringSerializer.instance);
                builder.addModules(longModule);
            }
            builder.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    properties.isFailOnUnknownProperties());
        };
    }
}
