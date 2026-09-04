package io.github.coco.storage;

import java.util.Objects;
import java.util.Optional;

/**
 * 基于魔数签名的 Coco 上传内容校验器。
 * <p>
 * 先比对危险签名，再比对扩展名声明的格式：危险签名检查必须在前，否则一个改名为白名单扩展名的可执行文件
 * 会因为扩展名合法而被放行。
 * </p>
 * <p>
 * 校验能力受 {@link CocoFileSignatures} 的已知限制约束：无可靠魔数的扩展名不做内容判断，
 * ZIP 系格式只能确认是 ZIP 容器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-storage}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public class CocoSignatureContentValidator implements CocoContentValidator {

    private final CocoStorageProperties.ValidationProperties properties;

    /**
     * <p>
     * 创建魔数签名校验器。
     * </p>
     * @param properties 内容校验配置
     */
    public CocoSignatureContentValidator(CocoStorageProperties.ValidationProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * <p>
     * 按危险签名、扩展名一致性的顺序校验上传内容。
     * </p>
     * @param probe 内容探测快照
     * @throws CocoStorageException 命中危险签名或魔数与扩展名不一致时抛出
     */
    @Override
    public void validate(CocoContentProbe probe) {
        CocoContentProbe checked = Objects.requireNonNull(probe, "probe must not be null");
        byte[] probeBytes = checked.probeBytes();
        if (this.properties.isRejectDangerousSignatures()) {
            Optional<CocoFileSignature> dangerous = CocoFileSignatures.findDangerous(probeBytes);
            if (dangerous.isPresent()) {
                throw new CocoStorageException(CocoStorageErrorCode.DANGEROUS_CONTENT, dangerous.get().label());
            }
        }
        if (this.properties.isRequireSignatureMatch()
                && !CocoFileSignatures.matchesExtension(checked.extension(), probeBytes)) {
            throw new CocoStorageException(CocoStorageErrorCode.SIGNATURE_MISMATCH, checked.extension());
        }
    }
}
