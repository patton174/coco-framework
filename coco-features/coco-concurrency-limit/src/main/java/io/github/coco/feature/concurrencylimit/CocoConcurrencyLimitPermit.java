package io.github.coco.feature.concurrencylimit;

/**
 * 成功申请并发配额后由存储返回的不透明许可。
 * <p>
 * 调用方只能把许可交还给创建它的 {@link CocoConcurrencyLimitStore}，不得依赖具体实现内容。
 * </p>
 */
public interface CocoConcurrencyLimitPermit {
}
