package io.github.coco.sample.basic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * # Coco 基础示例应用
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-sample-basic`
 *
 * 展示业务项目如何以 Spring Boot 应用方式接入 Coco starter。
 *
 * @author patton174
 * @since 1.0.0
 */
@SpringBootApplication
public class CocoSampleBasicApplication {

    public static void main(String[] args) {
        SpringApplication.run(CocoSampleBasicApplication.class, args);
    }
}
