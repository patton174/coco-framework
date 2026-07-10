package io.github.coco.feature.codegen.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.coco.feature.codegen.internal.CocoGeneratedPathValidator;

/**
 * Coco 生成文件安全写入器。
 * <p>
 * 写入器在产生任何磁盘变更前完成全部路径、重复输出、父路径和已有文件碰撞预检。
 * 默认拒绝覆盖，且 dry-run 不会创建目录或文件。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoGeneratedFileWriter {

    private final Charset encoding;

    /**
     * <p>
     * 使用 UTF-8 创建文件写入器。
     * </p>
     */
    public CocoGeneratedFileWriter() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * <p>
     * 使用指定编码名称创建文件写入器。
     * </p>
     * @param encoding 输出编码名称
     */
    public CocoGeneratedFileWriter(String encoding) {
        this(requireCharset(encoding));
    }

    /**
     * <p>
     * 使用指定字符集创建文件写入器。
     * </p>
     * @param encoding 输出字符集
     */
    public CocoGeneratedFileWriter(Charset encoding) {
        this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
    }

    /**
     * <p>
     * 使用默认选项写入生成结果。
     * </p>
     * @param outputDirectory 输出根目录
     * @param result 生成结果
     * @return 规范化后的目标文件列表
     */
    public List<Path> write(Path outputDirectory, CocoCodegenResult result) {
        return write(outputDirectory, result, CocoGeneratedFileWriteOptions.defaults());
    }

    /**
     * <p>
     * 在整批预检通过后写入生成结果。
     * </p>
     * @param outputDirectory 输出根目录
     * @param result 生成结果
     * @param options 写入选项
     * @return 规范化后的目标文件列表
     */
    public List<Path> write(Path outputDirectory, CocoCodegenResult result, CocoGeneratedFileWriteOptions options) {
        Path root = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null")
                .toAbsolutePath().normalize();
        CocoCodegenResult checkedResult = Objects.requireNonNull(result, "result must not be null");
        CocoGeneratedFileWriteOptions checkedOptions = Objects.requireNonNull(options, "options must not be null");
        List<PlannedFile> plannedFiles = plan(root, checkedResult.files());

        if (!checkedOptions.dryRun()) {
            preflight(root, plannedFiles, checkedOptions.overwrite());
            writeAll(plannedFiles, checkedOptions.overwrite());
        }
        return plannedFiles.stream().map(PlannedFile::target).toList();
    }

    private static List<PlannedFile> plan(Path root, List<CocoGeneratedFile> files) {
        Map<Path, PlannedFile> plannedFiles = new LinkedHashMap<>();
        for (CocoGeneratedFile file : files) {
            CocoGeneratedFile checkedFile = Objects.requireNonNull(file, "generated file must not be null");
            String normalizedPath = CocoGeneratedPathValidator.normalizeRelativePath(checkedFile.path());
            Path target = root.resolve(normalizedPath).normalize();
            if (!target.startsWith(root)) {
                throw new CocoCodegenException("generated path escapes output directory: " + checkedFile.path());
            }
            PlannedFile previous = plannedFiles.putIfAbsent(target, new PlannedFile(target, checkedFile));
            if (previous != null) {
                throw new CocoCodegenException("duplicate generated output: " + normalizedPath);
            }
        }
        return List.copyOf(plannedFiles.values());
    }

    private static void preflight(Path root, List<PlannedFile> plannedFiles, boolean overwrite) {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(root)) {
            throw new CocoCodegenException("output directory is not a directory: " + root);
        }
        List<Path> collisions = new ArrayList<>();
        for (PlannedFile plannedFile : plannedFiles) {
            Path target = plannedFile.target;
            validateParents(root, target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(target)
                        || Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                        || !overwrite) {
                    collisions.add(target);
                }
            }
        }
        if (!collisions.isEmpty()) {
            throw new CocoCodegenException("generated file collision: " + collisions);
        }
    }

    private static void validateParents(Path root, Path parent) {
        Path current = parent;
        while (current != null && current.startsWith(root)) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new CocoCodegenException("generated file parent is not a directory: " + current);
            }
            if (current.equals(root)) {
                return;
            }
            current = current.getParent();
        }
    }

    private void writeAll(List<PlannedFile> plannedFiles, boolean overwrite) {
        OpenOption[] openOptions = overwrite
                ? new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE }
                : new OpenOption[] { StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE };
        for (PlannedFile plannedFile : plannedFiles) {
            try {
                Files.createDirectories(plannedFile.target.getParent());
                Files.writeString(plannedFile.target, plannedFile.file.content(), this.encoding, openOptions);
            }
            catch (IOException ex) {
                throw new CocoCodegenException("failed to write generated file: " + plannedFile.target, ex);
            }
        }
    }

    private static Charset requireCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            throw new CocoCodegenException("codegen encoding must not be blank");
        }
        try {
            return Charset.forName(encoding.trim());
        }
        catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            throw new CocoCodegenException("unsupported codegen encoding: " + encoding, ex);
        }
    }

    private record PlannedFile(Path target, CocoGeneratedFile file) {
    }
}
