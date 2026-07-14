package io.github.coco.feature.codegen.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import io.github.coco.feature.codegen.internal.CocoGeneratedPathValidator;

/**
 * Coco 生成文件安全写入器。
 * <p>
 * 写入器在独占输出根锁内完成路径校验、碰撞预检、暂存、提交和可捕获异常回滚。
 * 默认拒绝覆盖，且 dry-run 不会创建目录或文件。普通文件系统无法保证 JVM 或机器崩溃时的多文件绝对原子性；
 * 此类会在提交前持久化恢复标记，异常终止或回滚不完整后拒绝继续生成，并保留业务文件备份供人工恢复。
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
            try (CocoOutputRootLock outputRootLock = CocoOutputRootLock.acquire(root)) {
                outputRootLock.requireNoRecoveryMarker();
                preflight(root, outputRootLock.canonicalRoot(), plannedFiles, checkedOptions.overwrite());
                writeAll(root, outputRootLock.canonicalRoot(), outputRootLock.recoveryMarker(),
                        plannedFiles, checkedOptions.overwrite());
            }
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

    private static void preflight(Path root, Path canonicalRoot, List<PlannedFile> plannedFiles,
            boolean overwrite) {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            BasicFileAttributes rootAttributes = CocoOutputRootLock.readAttributes(root);
            if (CocoOutputRootLock.isReparsePoint(rootAttributes) || !rootAttributes.isDirectory()) {
                throw new CocoCodegenException("output directory is not a safe directory: " + root);
            }
        }
        List<Path> collisions = new ArrayList<>();
        for (PlannedFile plannedFile : plannedFiles) {
            Path target = plannedFile.target;
            validateParents(root, target.getParent());
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                    && Files.exists(target.getParent(), LinkOption.NOFOLLOW_LINKS)) {
                CocoOutputRootLock.validateContainment(root, canonicalRoot, target.getParent());
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes targetAttributes = CocoOutputRootLock.readAttributes(target);
                if (CocoOutputRootLock.isReparsePoint(targetAttributes)
                        || !targetAttributes.isRegularFile() || !overwrite) {
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
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = CocoOutputRootLock.readAttributes(current);
                if (CocoOutputRootLock.isReparsePoint(attributes) || !attributes.isDirectory()) {
                    throw new CocoCodegenException(
                            "generated file parent must be a directory without reparse points: " + current);
                }
            }
            if (current.equals(root)) {
                return;
            }
            current = current.getParent();
        }
    }

    private void writeAll(Path root, Path canonicalRoot, Path recoveryMarker,
            List<PlannedFile> plannedFiles, boolean overwrite) {
        Path stagingDirectory = createStagingDirectory(root);
        Transaction transaction = null;
        try {
            List<StagedFile> stagedFiles = stage(plannedFiles, stagingDirectory);
            transaction = prepareTransaction(stagedFiles, stagingDirectory, recoveryMarker, overwrite);
            createRecoveryMarker(root, canonicalRoot, transaction);
            transaction.markerCreated = true;
            commit(root, canonicalRoot, transaction);
            Files.delete(recoveryMarker);
            transaction.markerCreated = false;
        }
        catch (IOException ex) {
            throw recoverOrMark(transaction, ex);
        }
        catch (CocoCodegenException ex) {
            throw recoverOrMark(transaction, ex);
        }
        finally {
            if (transaction == null || !transaction.markerCreated) {
                deleteRecursively(stagingDirectory);
            }
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
            stagedFiles.add(new StagedFile(index, staged, plannedFile.target, digest(staged)));
        }
        return stagedFiles;
    }

    private static Transaction prepareTransaction(List<StagedFile> stagedFiles, Path stagingDirectory,
            Path recoveryMarker, boolean overwrite) {
        List<TransactionFile> transactionFiles = new ArrayList<>(stagedFiles.size());
        for (StagedFile stagedFile : stagedFiles) {
            boolean originalExists = Files.exists(stagedFile.target(), LinkOption.NOFOLLOW_LINKS);
            if (originalExists) {
                BasicFileAttributes attributes = CocoOutputRootLock.readAttributes(stagedFile.target());
                if (CocoOutputRootLock.isReparsePoint(attributes) || !attributes.isRegularFile() || !overwrite) {
                    throw new CocoCodegenException("generated file collision during commit: " + stagedFile.target());
                }
            }
            Path backup = stagingDirectory.resolve("backup").resolve(Integer.toString(stagedFile.index()));
            transactionFiles.add(new TransactionFile(stagedFile, backup, originalExists));
        }
        return new Transaction(stagingDirectory, recoveryMarker, transactionFiles);
    }

    private void commit(Path root, Path canonicalRoot, Transaction transaction) throws IOException {
        for (TransactionFile transactionFile : transaction.files) {
            createTargetDirectories(root, transactionFile.stagedFile.target().getParent(),
                    transaction.createdDirectories);
            CocoOutputRootLock.validateContainment(root, canonicalRoot,
                    transactionFile.stagedFile.target().getParent());
            if (transactionFile.originalExists) {
                Files.createDirectories(transactionFile.backup.getParent());
                this.move(transactionFile.stagedFile.target(), transactionFile.backup);
                transactionFile.state = CommitState.BACKED_UP;
            }
            this.move(transactionFile.stagedFile.staged(), transactionFile.stagedFile.target());
            transactionFile.state = CommitState.REPLACED;
        }
    }

    private CocoCodegenException recoverOrMark(Transaction transaction, Exception failure) {
        if (transaction == null || !transaction.markerCreated) {
            return failure instanceof CocoCodegenException codegenException
                    ? codegenException : new CocoCodegenException("failed to write generated files", failure);
        }
        try {
            rollback(transaction);
            Files.deleteIfExists(transaction.recoveryMarker);
            transaction.markerCreated = false;
        }
        catch (IOException | CocoCodegenException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            return new CocoCodegenException("generated-file rollback is incomplete; recovery marker retained at "
                    + transaction.recoveryMarker, failure);
        }
        return failure instanceof CocoCodegenException codegenException
                ? codegenException : new CocoCodegenException("failed to write generated files", failure);
    }

    private static void createRecoveryMarker(Path root, Path canonicalRoot, Transaction transaction)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty("contract", "catchable-exception-rollback-only");
        properties.setProperty("outputRoot", root.toString());
        properties.setProperty("canonicalOutputRoot", canonicalRoot.toString());
        properties.setProperty("stagingDirectory", transaction.stagingDirectory.toString());
        properties.setProperty("file.count", Integer.toString(transaction.files.size()));
        for (int index = 0; index < transaction.files.size(); index++) {
            TransactionFile transactionFile = transaction.files.get(index);
            String prefix = "file." + index + ".";
            properties.setProperty(prefix + "target", transactionFile.stagedFile.target().toString());
            properties.setProperty(prefix + "backup", transactionFile.backup.toString());
            properties.setProperty(prefix + "originalExists", Boolean.toString(transactionFile.originalExists));
            properties.setProperty(prefix + "generatedSha256", transactionFile.stagedFile.digest());
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        properties.store(output, "Coco generated-file recovery marker");
        try {
            try (FileChannel channel = FileChannel.open(transaction.recoveryMarker,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(output.toByteArray());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
        }
        catch (IOException ex) {
            try {
                Files.deleteIfExists(transaction.recoveryMarker);
            }
            catch (IOException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }
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
        BasicFileAttributes attributes = CocoOutputRootLock.readAttributes(current);
        if (CocoOutputRootLock.isReparsePoint(attributes) || !attributes.isDirectory()) {
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
        Files.move(source, target);
    }

    private void rollback(Transaction transaction) {
        IOException failure = null;
        for (int index = transaction.files.size() - 1; index >= 0; index--) {
            try {
                rollback(transaction.files.get(index));
            }
            catch (IOException | CocoCodegenException ex) {
                failure = appendFailure(failure, asIOException(ex));
            }
        }
        for (int index = transaction.createdDirectories.size() - 1; index >= 0; index--) {
            try {
                Files.deleteIfExists(transaction.createdDirectories.get(index));
            }
            catch (IOException ex) {
                failure = appendFailure(failure, ex);
            }
        }
        if (failure != null) {
            throw new CocoCodegenException("failed to roll back generated files", failure);
        }
    }

    private void rollback(TransactionFile transactionFile) throws IOException {
        if (transactionFile.state == CommitState.REPLACED) {
            Path target = transactionFile.stagedFile.target();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = CocoOutputRootLock.readAttributes(target);
                if (CocoOutputRootLock.isReparsePoint(attributes) || !attributes.isRegularFile()
                        || !transactionFile.stagedFile.digest().equals(digest(target))) {
                    throw new IOException("generated target changed before rollback; preserving target and backup: "
                            + target);
                }
                Files.delete(target);
            }
            transactionFile.state = transactionFile.originalExists ? CommitState.BACKED_UP : CommitState.PLANNED;
        }
        if (transactionFile.state == CommitState.BACKED_UP) {
            Path target = transactionFile.stagedFile.target();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("target appeared before backup restoration; preserving target and backup: "
                        + target);
            }
            if (!Files.exists(transactionFile.backup, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("generated-file backup is missing: " + transactionFile.backup);
            }
            this.move(transactionFile.backup, target);
            transactionFile.state = CommitState.PLANNED;
        }
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static IOException appendFailure(IOException previous, IOException next) {
        if (previous == null) {
            return next;
        }
        previous.addSuppressed(next);
        return previous;
    }

    private static IOException asIOException(Exception failure) {
        return failure instanceof IOException ioException
                ? ioException : new IOException(failure.getMessage(), failure);
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

    private record StagedFile(int index, Path staged, Path target, String digest) {
    }

    private static final class Transaction {

        private final Path stagingDirectory;

        private final Path recoveryMarker;

        private final List<TransactionFile> files;

        private final List<Path> createdDirectories = new ArrayList<>();

        private boolean markerCreated;

        private Transaction(Path stagingDirectory, Path recoveryMarker, List<TransactionFile> files) {
            this.stagingDirectory = stagingDirectory;
            this.recoveryMarker = recoveryMarker;
            this.files = files;
        }
    }

    private static final class TransactionFile {

        private final StagedFile stagedFile;

        private final Path backup;

        private final boolean originalExists;

        private CommitState state = CommitState.PLANNED;

        private TransactionFile(StagedFile stagedFile, Path backup, boolean originalExists) {
            this.stagedFile = stagedFile;
            this.backup = backup;
            this.originalExists = originalExists;
        }
    }

    private enum CommitState {

        PLANNED,

        BACKED_UP,

        REPLACED
    }

    @FunctionalInterface
    interface FileMover {

        void move(Path source, Path target) throws IOException;
    }
}
