package io.github.coco.fixture.archive;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatures;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Framework-only Spring Boot packaging fixture.
 * <p>
 * This class exists solely to produce a reactor-built archive that exercises
 * feature manifest generation and disabled-feature package pruning.
 * </p>
 */
@SpringBootApplication
@CocoFeatures(disabled = {
        CocoFeature.MYBATIS_PLUS,
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION,
        CocoFeature.RATE_LIMIT,
        CocoFeature.IDEMPOTENCY,
        CocoFeature.CODEGEN
})
public class FeatureArchiveSmokeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeatureArchiveSmokeApplication.class, args);
    }
}
