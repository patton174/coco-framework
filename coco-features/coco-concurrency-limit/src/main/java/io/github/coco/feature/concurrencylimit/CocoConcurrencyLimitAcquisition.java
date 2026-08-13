package io.github.coco.feature.concurrencylimit;

import java.util.List;
import java.util.Objects;

/**
 * 原子并发许可申请结果。
 *
 * @param acquired 是否成功申请全部约束
 * @param permit 成功时返回的不透明许可
 * @param snapshots 各维度申请后的容量快照
 * @param rejectedDimension 拒绝申请的维度
 * @param rejectionReason 拒绝原因
 */
public record CocoConcurrencyLimitAcquisition(boolean acquired, CocoConcurrencyLimitPermit permit,
        List<CocoConcurrencyLimitSnapshot> snapshots, CocoConcurrencyLimitDimension rejectedDimension,
        CocoConcurrencyLimitRejectionReason rejectionReason) {

    /**
     * 校验并复制申请结果。
     */
    public CocoConcurrencyLimitAcquisition {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (acquired) {
            Objects.requireNonNull(permit, "permit must not be null for an acquired result");
            if (rejectedDimension != null || rejectionReason != null) {
                throw new IllegalArgumentException("acquired result must not contain rejection metadata");
            }
        }
        else {
            if (permit != null) {
                throw new IllegalArgumentException("rejected result must not contain a permit");
            }
            Objects.requireNonNull(rejectedDimension, "rejectedDimension must not be null");
            Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
        }
    }

    /**
     * 创建成功申请结果。
     * @param permit 存储许可
     * @param snapshots 各维度快照
     * @return 成功申请结果
     */
    public static CocoConcurrencyLimitAcquisition granted(CocoConcurrencyLimitPermit permit,
            List<CocoConcurrencyLimitSnapshot> snapshots) {
        return new CocoConcurrencyLimitAcquisition(true, permit, snapshots, null, null);
    }

    /**
     * 创建拒绝申请结果。
     * @param snapshots 各维度快照
     * @param dimension 拒绝维度
     * @param reason 拒绝原因
     * @return 拒绝申请结果
     */
    public static CocoConcurrencyLimitAcquisition rejected(List<CocoConcurrencyLimitSnapshot> snapshots,
            CocoConcurrencyLimitDimension dimension, CocoConcurrencyLimitRejectionReason reason) {
        return new CocoConcurrencyLimitAcquisition(false, null, snapshots, dimension, reason);
    }
}
