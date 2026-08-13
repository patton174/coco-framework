package io.github.coco.feature.idempotency;

/**
 * Coco 请求幂等功能标识。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoIdempotencyFeature {

    /** 功能标识。 */
    public static final String ID = "idempotency";

    /** 配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.idempotency";

    private CocoIdempotencyFeature() {
    }
}
