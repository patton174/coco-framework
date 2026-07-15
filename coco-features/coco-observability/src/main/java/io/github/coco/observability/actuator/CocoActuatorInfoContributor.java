package io.github.coco.observability.actuator;

import io.github.coco.observability.CocoObservabilityStatusProvider;
import java.util.Objects;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/**
 * Coco 可观测性信息贡献器。
 */
public final class CocoActuatorInfoContributor implements InfoContributor {

    private final CocoObservabilityStatusProvider statusProvider;

    public CocoActuatorInfoContributor(CocoObservabilityStatusProvider statusProvider) {
        this.statusProvider = Objects.requireNonNull(statusProvider, "statusProvider must not be null");
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("coco", this.statusProvider.status());
    }
}
