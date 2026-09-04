package io.github.coco.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Coco 内置文件魔数签名表。
 * <p>
 * 提供两张表：按扩展名索引的允许类型签名表，以及对所有上传内容统一比对的危险签名表。
 * 前者用于校验“声明的扩展名与实际内容是否一致”，后者用于识别伪装成普通文档的可执行文件。
 * </p>
 * <p>
 * 已知限制一：部分扩展名没有可靠魔数，包括 {@code txt}、{@code csv}、{@code json}、{@code xml}、{@code md}。
 * 这些扩展名不在签名表中，{@link #matchesExtension(String, byte[])} 对它们一律返回 {@code true}，
 * 因此这类文件实际上只受扩展名白名单约束，魔数校验不提供任何额外保护。
 * </p>
 * <p>
 * 已知限制二：ZIP 系格式（{@code docx}、{@code xlsx}、{@code pptx}、{@code zip}）共享完全相同的魔数，
 * 无法彼此区分，也无法与普通 ZIP 压缩包区分。签名命中只能证明“这是一个 ZIP 容器”，
 * 不能证明它是声明的那种 Office 文档。需要精确判定时必须解析容器内部结构。
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
public final class CocoFileSignatures {

    private static final List<CocoFileSignature> ZIP_CONTAINER = List.of(
            CocoFileSignature.of("ZIP (local file header)", 0, 0x50, 0x4B, 0x03, 0x04),
            CocoFileSignature.of("ZIP (empty archive)", 0, 0x50, 0x4B, 0x05, 0x06),
            CocoFileSignature.of("ZIP (spanned archive)", 0, 0x50, 0x4B, 0x07, 0x08));

    private static final Map<String, List<CocoFileSignature>> ALLOWED_SIGNATURES = allowedSignatures();

    private static final List<CocoFileSignature> DANGEROUS_SIGNATURES = List.of(
            CocoFileSignature.of("PE executable (EXE/DLL)", 0, 0x4D, 0x5A),
            CocoFileSignature.of("ELF executable", 0, 0x7F, 0x45, 0x4C, 0x46),
            CocoFileSignature.of("Java class file", 0, 0xCA, 0xFE, 0xBA, 0xBE),
            CocoFileSignature.of("Shell script shebang", 0, 0x23, 0x21));

    private CocoFileSignatures() {
    }

    /**
     * <p>
     * 在危险签名表中查找命中的签名。
     * </p>
     * <p>
     * 该检查与扩展名无关，对所有上传内容执行，因此能识别改名为 {@code .png} 的可执行文件。
     * </p>
     * @param probe 探测字节
     * @return 首个命中的危险签名；没有命中时为空
     */
    public static Optional<CocoFileSignature> findDangerous(byte[] probe) {
        for (CocoFileSignature signature : DANGEROUS_SIGNATURES) {
            if (signature.matches(probe)) {
                return Optional.of(signature);
            }
        }
        return Optional.empty();
    }

    /**
     * <p>
     * 判断扩展名是否登记了魔数签名。
     * </p>
     * @param extension 小写扩展名，不含点号
     * @return 存在签名时返回 {@code true}
     */
    public static boolean hasKnownSignature(String extension) {
        return ALLOWED_SIGNATURES.containsKey(normalize(extension));
    }

    /**
     * <p>
     * 判断探测字节是否与扩展名声明的格式一致。
     * </p>
     * <p>
     * 扩展名未登记签名时返回 {@code true}：没有可比对的魔数，此处不做判断，由扩展名白名单负责约束。
     * </p>
     * @param extension 小写扩展名，不含点号
     * @param probe 探测字节
     * @return 无签名可比对或任一签名命中时返回 {@code true}
     */
    public static boolean matchesExtension(String extension, byte[] probe) {
        List<CocoFileSignature> signatures = ALLOWED_SIGNATURES.get(normalize(extension));
        if (signatures == null) {
            return true;
        }
        for (CocoFileSignature signature : signatures) {
            if (signature.matches(probe)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<CocoFileSignature>> allowedSignatures() {
        List<CocoFileSignature> jpeg = List.of(CocoFileSignature.of("JPEG", 0, 0xFF, 0xD8, 0xFF));
        Map<String, List<CocoFileSignature>> signatures = new LinkedHashMap<>();
        signatures.put("jpg", jpeg);
        signatures.put("jpeg", jpeg);
        signatures.put("png", List.of(CocoFileSignature.of("PNG", 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)));
        signatures.put("gif", List.of(CocoFileSignature.of("GIF", 0, 0x47, 0x49, 0x46, 0x38)));
        signatures.put("webp", List.of(new CocoFileSignature("WebP",
                List.of(new CocoFileSignature.Part(0, new byte[] { 0x52, 0x49, 0x46, 0x46 }),
                        new CocoFileSignature.Part(8, new byte[] { 0x57, 0x45, 0x42, 0x50 })))));
        signatures.put("pdf", List.of(CocoFileSignature.of("PDF", 0, 0x25, 0x50, 0x44, 0x46)));
        signatures.put("zip", ZIP_CONTAINER);
        signatures.put("docx", ZIP_CONTAINER);
        signatures.put("xlsx", ZIP_CONTAINER);
        signatures.put("pptx", ZIP_CONTAINER);
        signatures.put("mp4", List.of(CocoFileSignature.of("MP4 (ftyp)", 4, 0x66, 0x74, 0x79, 0x70)));
        return Map.copyOf(signatures);
    }
}
