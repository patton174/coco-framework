package io.github.coco.storage;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 空实现 Coco 上传内容扫描器。
 * <p>
 * 不做任何检查，仅在首次使用时记录一条 INFO 日志提示未接入扫描引擎。需要真实恶意软件检测时，
 * 业务方应声明自己的 {@link CocoFileScanner} Bean 对接 ClamAV 等外部引擎。
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
public final class NoOpCocoFileScanner implements CocoFileScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpCocoFileScanner.class);

    private static final AtomicBoolean NOTICE_LOGGED = new AtomicBoolean();

    /**
     * <p>
     * 创建空实现扫描器。
     * </p>
     */
    public NoOpCocoFileScanner() {
    }

    /**
     * <p>
     * 不执行任何扫描。
     * </p>
     * @param probe 内容探测快照
     */
    @Override
    public void scan(CocoContentProbe probe) {
        if (NOTICE_LOGGED.compareAndSet(false, true)) {
            LOGGER.info("Coco storage uses NoOpCocoFileScanner; no malware scanning is performed. "
                    + "Declare a CocoFileScanner bean backed by an external engine such as ClamAV to enable it.");
        }
    }
}
