package io.github.coco.sample.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Coco 基础示例应用。
 * <p>
 * 展示业务项目如何以 Spring Boot 应用方式接入 Coco starter。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * <p>
 * 代码注释采用标准 JavaDoc HTML 标签，不使用 Markdown 语法。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
@SpringBootApplication
public class CocoSampleBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(CocoSampleBasicApplication.class, args);
    }
}
