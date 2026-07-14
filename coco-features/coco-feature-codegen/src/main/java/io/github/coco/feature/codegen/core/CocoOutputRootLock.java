package io.github.coco.feature.codegen.core;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 输出根目录的进程内和跨进程独占锁。
 * <p>状态位于 canonical 输出根之外的同文件系统父目录。持久 lock file 不得在释放时删除，
 * 否则 Unix 上已打开文件被 unlink 后会形成新的锁 inode。</p>
 */
final class CocoOutputRootLock implements AutoCloseable {

    private static final ConcurrentMap<Path, ReentrantLock> PROCESS_LOCKS = new ConcurrentHashMap<>();

    private static final String STATE_DIRECTORY_NAME = ".coco-codegen-state";

    private static final String LOCK_FILE_NAME = "writer.lock";

    private static final String RECOVERY_MARKER_NAME = "recovery.properties";

    private final Path canonicalRoot;

    private final Path lockFile;

    private final Path recoveryMarker;

    private final ReentrantLock processLock;

    private final FileChannel channel;

    private final FileLock fileLock;

    private CocoOutputRootLock(Path canonicalRoot, Path lockFile, Path recoveryMarker,
            ReentrantLock processLock, FileChannel channel, FileLock fileLock) {
        this.canonicalRoot = canonicalRoot;
        this.lockFile = lockFile;
        this.recoveryMarker = recoveryMarker;
        this.processLock = processLock;
        this.channel = channel;
        this.fileLock = fileLock;
    }

    static CocoOutputRootLock acquire(Path root) {
        Path canonicalRoot = canonicalize(root);
        validateOutputRoot(canonicalRoot);
        ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(canonicalRoot, key -> new ReentrantLock());
        if (!processLock.tryLock()) {
            throw locked(canonicalRoot, "output root is already locked in this process", null);
        }

        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            StatePaths statePaths = statePaths(root, canonicalRoot);
            validateLockFile(statePaths.lockFile());
            channel = FileChannel.open(statePaths.lockFile(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try {
                fileLock = channel.tryLock();
            }
            catch (OverlappingFileLockException ex) {
                throw locked(canonicalRoot, "output root lock overlaps another lock in this process", ex);
            }
            if (fileLock == null) {
                throw locked(canonicalRoot, "output root is locked by another process", null);
            }
            return new CocoOutputRootLock(canonicalRoot, statePaths.lockFile(), statePaths.recoveryMarker(),
                    processLock, channel, fileLock);
        }
        catch (IOException ex) {
            closeQuietly(fileLock, channel);
            processLock.unlock();
            throw locked(canonicalRoot, "failed to acquire output root lock", ex);
        }
        catch (RuntimeException ex) {
            closeQuietly(fileLock, channel);
            processLock.unlock();
            throw ex;
        }
    }

    Path canonicalRoot() {
        return this.canonicalRoot;
    }

    Path recoveryMarker() {
        return this.recoveryMarker;
    }

    void requireNoRecoveryMarker() {
        if (Files.exists(this.recoveryMarker, LinkOption.NOFOLLOW_LINKS)) {
            throw new CocoCodegenException("generated-file recovery is required for output root "
                    + this.canonicalRoot + "; recovery marker: " + this.recoveryMarker);
        }
    }

    static Path lockFilePath(Path root) {
        Path canonicalRoot = canonicalize(root);
        return statePaths(root, canonicalRoot).lockFile();
    }

    static Path recoveryMarkerPath(Path root) {
        Path canonicalRoot = canonicalize(root);
        return statePaths(root, canonicalRoot).recoveryMarker();
    }

    static void validateOutputRoot(Path root) {
        Path canonicalRoot = canonicalize(root);
        if (canonicalRoot.getParent() == null) {
            throw new CocoCodegenException("filesystem root cannot be used as a codegen output directory: "
                    + canonicalRoot);
        }
        for (Path segment : canonicalRoot) {
            if (isStateDirectoryName(segment.toString())) {
                throw new CocoCodegenException("codegen state root cannot be used as a codegen output directory: "
                        + canonicalRoot);
            }
        }
    }

    static void validateExistingPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current != null && Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            rejectReparsePoint(current);
        }
        for (Path segment : absolute) {
            current = current == null ? segment : current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            rejectReparsePoint(current);
        }
    }

    static void validateContainment(Path root, Path canonicalRoot, Path parent) {
        validateExistingPath(parent);
        try {
            Path realRoot = root.toRealPath();
            Path realParent = parent.toRealPath();
            if (!realRoot.equals(canonicalRoot) || !realParent.startsWith(realRoot)) {
                throw new CocoCodegenException("generated file parent escapes canonical output root: " + parent);
            }
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to resolve generated file parent: " + parent, ex);
        }
    }

    static BasicFileAttributes readAttributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to inspect generated path: " + path, ex);
        }
    }

    static boolean isReparsePoint(BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
    }

    @Override
    public void close() {
        IOException failure = null;
        try {
            this.fileLock.release();
        }
        catch (IOException ex) {
            failure = ex;
        }
        try {
            this.channel.close();
        }
        catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            }
            else {
                failure.addSuppressed(ex);
            }
        }
        finally {
            this.processLock.unlock();
        }
        if (failure != null) {
            throw new CocoCodegenException("failed to release output root lock file: " + this.lockFile, failure);
        }
    }

    private static Path canonicalize(Path root) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        List<Path> missingSegments = new ArrayList<>();
        Path existing = absoluteRoot;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missingSegments.add(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new CocoCodegenException("output directory has no existing ancestor: " + absoluteRoot);
        }
        validateExistingPath(existing);
        BasicFileAttributes attributes = readAttributes(existing);
        if (!attributes.isDirectory()) {
            throw new CocoCodegenException("output directory ancestor is not a directory: " + existing);
        }
        try {
            Path canonical = existing.toRealPath();
            for (int index = missingSegments.size() - 1; index >= 0; index--) {
                canonical = canonical.resolve(missingSegments.get(index));
            }
            return canonical.normalize();
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to resolve canonical output root: " + absoluteRoot, ex);
        }
    }

    private static boolean isStateDirectoryName(String name) {
        return name.equals(STATE_DIRECTORY_NAME)
                || (Path.of(name).getFileSystem().getSeparator().equals("\\")
                && name.equalsIgnoreCase(STATE_DIRECTORY_NAME));
    }

    private static StatePaths statePaths(Path root, Path canonicalRoot) {
        try {
            Files.createDirectories(root);
            validateExistingPath(root);
            Path realRoot = root.toRealPath();
            if (!realRoot.equals(canonicalRoot)) {
                throw new CocoCodegenException("output root changed while acquiring its lock: " + root);
            }
            Path canonicalParent = realRoot.getParent();
            if (canonicalParent == null) {
                throw new CocoCodegenException("filesystem root cannot be used as a codegen output directory: "
                        + realRoot);
            }
            Path stateRoot = canonicalParent.resolve(STATE_DIRECTORY_NAME);
            Files.createDirectories(stateRoot);
            Path realStateRoot = requireSafeDirectory(stateRoot, canonicalParent,
                    "codegen state root is not a safe directory");
            if (Files.isSameFile(realRoot, realStateRoot)) {
                throw new CocoCodegenException("codegen state root must be outside output root: " + realRoot);
            }
            Path stateDirectory = realStateRoot.resolve(rootKey(realRoot));
            Files.createDirectories(stateDirectory);
            Path realStateDirectory = requireSafeDirectory(stateDirectory, realStateRoot,
                    "codegen state directory is not a safe directory");
            if (!Files.getFileStore(realStateDirectory).equals(Files.getFileStore(realRoot))) {
                throw new CocoCodegenException("codegen state directory must use the output filesystem: "
                        + stateDirectory);
            }
            return new StatePaths(realStateDirectory.resolve(LOCK_FILE_NAME),
                    realStateDirectory.resolve(RECOVERY_MARKER_NAME));
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to prepare codegen lock state directory", ex);
        }
    }

    private static Path requireSafeDirectory(Path directory, Path expectedParent, String detail) throws IOException {
        BasicFileAttributes attributes = readAttributes(directory);
        if (!attributes.isDirectory() || isReparsePoint(attributes)) {
            throw new CocoCodegenException(detail + ": " + directory);
        }
        Path realDirectory = directory.toRealPath();
        Path realParent = realDirectory.getParent();
        if (realParent == null || !Files.isSameFile(realParent, expectedParent.toRealPath())) {
            throw new CocoCodegenException(detail + ": " + directory);
        }
        return realDirectory;
    }

    private static String rootKey(Path canonicalRoot) {
        String value = canonicalRoot.toString();
        if (canonicalRoot.getFileSystem().getSeparator().equals("\\")) {
            value = value.toLowerCase(Locale.ROOT);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void validateLockFile(Path lockFile) {
        if (!Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BasicFileAttributes attributes = readAttributes(lockFile);
        if (!attributes.isRegularFile() || isReparsePoint(attributes)) {
            throw new CocoCodegenException("codegen lock file is not a safe regular file: " + lockFile);
        }
    }

    private static void rejectReparsePoint(Path path) {
        BasicFileAttributes attributes = readAttributes(path);
        if (isReparsePoint(attributes)) {
            throw new CocoCodegenException("generated path must not contain a symbolic link or reparse point: " + path);
        }
    }

    private static CocoCodegenException locked(Path canonicalRoot, String detail, Throwable cause) {
        String message = detail + ": " + canonicalRoot;
        return cause == null ? new CocoCodegenException(message) : new CocoCodegenException(message, cause);
    }

    private static void closeQuietly(FileLock fileLock, FileChannel channel) {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        }
        catch (IOException ignored) {
        }
        try {
            if (channel != null) {
                channel.close();
            }
        }
        catch (IOException ignored) {
        }
    }

    private record StatePaths(Path lockFile, Path recoveryMarker) {
    }
}
