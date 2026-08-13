package io.github.coco.feature.security.authorization;

/**
 * Coco 方法授权集合的组合方式。
 * <p>
 * {@link #ALL} 要求主体拥有集合中的全部项目，{@link #ANY} 要求主体拥有其中任一项目。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoAuthorizationMode {

    /** 要求拥有集合中的全部项目。 */
    ALL,

    /** 要求拥有集合中的任一项目。 */
    ANY
}
