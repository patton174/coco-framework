package io.github.coco.sample.full;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Coco 完整能力示例应用。
 * <p>
 * 使用单一 starter 展示安全上下文、租户隔离、数据权限、审计和 MyBatis-Plus 的协作边界。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@SpringBootApplication
public class CocoSampleFullApplication {

    public static void main(String[] args) {
        SpringApplication.run(CocoSampleFullApplication.class, args);
    }
}
