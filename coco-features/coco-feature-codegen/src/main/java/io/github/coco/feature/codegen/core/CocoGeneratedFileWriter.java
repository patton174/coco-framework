package io.github.coco.feature.codegen.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private final FileMover fileMover;

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
        this(encoding, CocoGeneratedFileWriter::moveFile);
    }

    CocoGeneratedFileWriter(Charset encoding, FileMover fileMover) {
        this.encoding = Objects.requireNonNull(encoding, "encoding must not be null");
        this.fileMover = Objects.requireNonNull(fileMover, "fileMover must not be null");
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
            writeAll(root, plannedFiles);
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
        if (Files.isSymbolicLink(root)) {
            throw new CocoCodegenException("output directory must not be a symbolic link: " + root);
        }
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
            if (Files.isSymbolicLink(current)) {
                throw new CocoCodegenException("generated file parent must not be a symbolic link: " + current);
            }
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

    private void writeAll(Path root, List<PlannedFile> plannedFiles) {
        Path stagingDirectory = createStagingDirectory(root);
        List<Path> createdDirectories = new ArrayList<>();
        List<CommittedFile> committedFiles = new ArrayList<>();
        try {
            List<StagedFile> stagedFiles = stage(plannedFiles, stagingDirectory);
            for (StagedFile stagedFile : stagedFiles) {
                createTargetDirectories(root, stagedFile.target().getParent(), createdDirectories);
                Path backup = stagingDirectory.resolve("backup").resolve(Integer.toString(stagedFile.index()));
                CommittedFile committedFile = new CommittedFile(stagedFile.target(), backup,
                        Files.exists(stagedFile.target(), LinkOption.NOFOLLOW_LINKS));
                committedFiles.add(committedFile);
                if (committedFile.existed()) {
                    Files.createDirectories(backup.getParent());
                    this.move(stagedFile.target(), backup);
                }
                this.move(stagedFile.staged(), stagedFile.target());
            }
        }
        catch (IOException ex) {
            try {
                rollback(committedFiles, createdDirectories);
            }
            catch (CocoCodegenException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw new CocoCodegenException("failed to write generated files", ex);
        }
        catch (CocoCodegenException ex) {
            try {
                rollback(committedFiles, createdDirectories);
            }
            catch (CocoCodegenException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw ex;
        }
        finally {
            deleteRecursively(stagingDirectory);
        }
    }

    private List<StagedFile> stage(List<PlannedFile> plannedFiles, Path stagingDirectory) throws IOException {
        List<StagedFile> stagedFiles = new ArrayList<>(plannedFiles.size());
        for (int index = 0; index < plannedFiles.size(); index++) {
            PlannedFile plannedFile = plannedFiles.get(index);
            Path staged = stagingDirectory.resolve("files").resolve(Integer.toString(index));
            Files.createDirectories(staged.getParent());
            Files.writeString(staged, plannedFile.file.content(), this.encoding,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            stagedFiles.add(new StagedFile(index, staged, plannedFile.target));
        }
        return stagedFiles;
    }

    private static Path createStagingDirectory(Path root) {
        Path parent = nearestExistingParent(root);
        try {
            return Files.createTempDirectory(parent, ".coco-codegen-");
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to create generated-file staging directory", ex);
        }
    }

    private static Path nearestExistingParent(Path root) {
        Path current = root.getParent();
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current == null || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new CocoCodegenException("output directory has no writable parent: " + root);
        }
        return current;
    }

    private static void createTargetDirectories(Path root, Path targetParent, List<Path> createdDirectories)
            throws IOException {
        List<Path> missingDirectories = new ArrayList<>();
        Path current = targetParent;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missingDirectories.add(current);
            current = current.getParent();
        }
        Path rootParent = root.getParent();
        if (current == null || (rootParent != null && !current.startsWith(rootParent))) {
            throw new CocoCodegenException("generated file parent escapes output directory: " + targetParent);
        }
        if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new CocoCodegenException("generated file parent is not a directory: " + current);
        }
        for (int index = missingDirectories.size() - 1; index >= 0; index--) {
            Path directory = missingDirectories.get(index);
            Files.createDirectory(directory);
            createdDirectories.add(directory);
        }
    }

    private void move(Path source, Path target) throws IOException {
        this.fileMover.move(source, target);
    }

    private static void moveFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void rollback(List<CommittedFile> committedFiles, List<Path> createdDirectories) {
        IOException failure = null;
        for (int index = committedFiles.size() - 1; index >= 0; index--) {
            CommittedFile committedFile = committedFiles.get(index);
            try {
                Files.deleteIfExists(committedFile.target());
                if (committedFile.existed() && Files.exists(committedFile.backup())) {
                    move(committedFile.backup(), committedFile.target());
                }
            }
            catch (IOException ex) {
                failure = appendFailure(failure, ex);
            }
        }
        for (int index = createdDirectories.size() - 1; index >= 0; index--) {
            try {
                Files.deleteIfExists(createdDirectories.get(index));
            }
            catch (IOException ex) {
                failure = appendFailure(failure, ex);
            }
        }
        if (failure != null) {
            throw new CocoCodegenException("failed to roll back generated files", failure);
        }
    }

    private static IOException appendFailure(IOException previous, IOException next) {
        if (previous == null) {
            return next;
        }
        previous.addSuppressed(next);
        return previous;
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                }
                catch (IOException ignored) {
                    // The original failure is more useful than an unremovable temporary file.
                }
            });
        }
        catch (IOException ignored) {
            // The original failure is more useful than an unremovable temporary file.
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

    private record StagedFile(int index, Path staged, Path target) {
    }

    private record CommittedFile(Path target, Path backup, boolean existed) {
    }

    @FunctionalInterface
    interface FileMover {

        void move(Path source, Path target) throws IOException;
    }
}
