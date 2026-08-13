package io.github.coco.context;

/**
 * Coco 上下文快照贡献者。
 *
 * @author patton174
 * @since 1.0.0
 */
public interface CocoContextSnapshotContributor {

    /** @return 稳定且唯一的贡献者标识 */
    String id();

    /** @return 组合捕获顺序，数值越小越靠前 */
    default int order() {
        return 0;
    }

    /** @return 当前提交线程的上下文快照 */
    CocoContextSnapshot capture();
}
