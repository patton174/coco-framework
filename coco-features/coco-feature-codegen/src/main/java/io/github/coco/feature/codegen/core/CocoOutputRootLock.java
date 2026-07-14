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

/** 输出根目录的进程内和跨进程独占锁。 */
final class CocoOutputRootLock implements AutoCloseable {

    private static final ConcurrentMap<Path, ReentrantLock> PROCESS_LOCKS = new ConcurrentHashMap<>();

    private static final String STATE_DIRECTORY_NAME = "coco-codegen-state";

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
        ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(canonicalRoot, key -> new ReentrantLock());
        if (!processLock.tryLock()) {
            throw locked(canonicalRoot, "output root is already locked in this process", null);
        }

        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            StatePaths statePaths = statePaths(canonicalRoot);
            channel = FileChannel.open(statePaths.lockFile(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
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
        return statePaths(canonicalize(root)).lockFile();
    }

    static Path recoveryMarkerPath(Path root) {
        return statePaths(canonicalize(root)).recoveryMarker();
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

    private static StatePaths statePaths(Path canonicalRoot) {
        try {
            Path stateDirectory = Path.of(System.getProperty("java.io.tmpdir"), STATE_DIRECTORY_NAME)
                    .toAbsolutePath().normalize();
            Files.createDirectories(stateDirectory);
            BasicFileAttributes attributes = readAttributes(stateDirectory);
            if (!attributes.isDirectory() || isReparsePoint(attributes)) {
                throw new CocoCodegenException("codegen state directory is not a safe directory: " + stateDirectory);
            }
            Path realStateDirectory = stateDirectory.toRealPath();
            String key = rootKey(canonicalRoot);
            return new StatePaths(realStateDirectory.resolve(key + ".lock"),
                    realStateDirectory.resolve(key + ".recovery"));
        }
        catch (IOException ex) {
            throw new CocoCodegenException("failed to prepare codegen lock state directory", ex);
        }
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
