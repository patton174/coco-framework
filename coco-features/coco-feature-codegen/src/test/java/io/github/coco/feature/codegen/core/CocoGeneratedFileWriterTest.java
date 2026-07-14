package io.github.coco.feature.codegen.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class CocoGeneratedFileWriterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesFilesWithConfiguredEncoding() throws IOException {
        Path output = this.tempDirectory.resolve("generated");
        CocoCodegenResult result = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile("com/example/Sample.java", "class Sample {}")));

        List<Path> written = new CocoGeneratedFileWriter("UTF-16LE").write(output, result);

        assertThat(written).containsExactly(output.resolve("com/example/Sample.java").toAbsolutePath());
        assertThat(Files.readAllBytes(written.get(0)))
                .containsExactly("class Sample {}".getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
    }

    @Test
    void rejectsAnyExistingTargetBeforeWritingTheBatch() throws IOException {
        Path output = this.tempDirectory.resolve("collision");
        Path existing = output.resolve("second.txt");
        Files.createDirectories(output);
        Files.writeString(existing, "existing");
        CocoCodegenResult result = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile("first.txt", "first"),
                new CocoGeneratedFile("second.txt", "second")));

        assertThatThrownBy(() -> new CocoGeneratedFileWriter().write(output, result))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("collision");
        assertThat(output.resolve("first.txt")).doesNotExist();
        assertThat(Files.readString(existing)).isEqualTo("existing");
    }

    @Test
    void overwritesOnlyWhenExplicitlyEnabled() throws IOException {
        Path output = this.tempDirectory.resolve("overwrite");
        Path target = output.resolve("value.txt");
        Files.createDirectories(output);
        Files.writeString(target, "old");
        CocoCodegenResult result = CocoCodegenResult.of(List.of(new CocoGeneratedFile("value.txt", "new")));

        new CocoGeneratedFileWriter().write(output, result, new CocoGeneratedFileWriteOptions(true, false));

        assertThat(Files.readString(target)).isEqualTo("new");
    }

    @Test
    void rollsBackCommittedFilesWhenWritingTheBatchFails() throws IOException {
        Path output = this.tempDirectory.resolve("atomic");
        Path existing = output.resolve("existing.txt");
        Files.createDirectories(output);
        Files.writeString(existing, "old");
        AtomicInteger moves = new AtomicInteger();
        CocoGeneratedFileWriter writer = new CocoGeneratedFileWriter(java.nio.charset.StandardCharsets.UTF_8,
                (source, target) -> {
                    if (moves.incrementAndGet() == 3) {
                        throw new IOException("simulated move failure");
                    }
                    Files.move(source, target);
                });
        CocoCodegenResult result = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile("existing.txt", "new"),
                new CocoGeneratedFile("new.txt", "new")));

        assertThatThrownBy(() -> writer.write(output, result, new CocoGeneratedFileWriteOptions(true, false)))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("failed to write generated files");
        assertThat(Files.readString(existing)).isEqualTo("old");
        assertThat(output.resolve("new.txt")).doesNotExist();
    }

    @Test
    void preservesExistingFileWhenBackupMoveFails() throws IOException {
        Path output = this.tempDirectory.resolve("backup-failure");
        Path existing = output.resolve("existing.txt");
        Files.createDirectories(output);
        Files.writeString(existing, "business-content");
        CocoGeneratedFileWriter writer = new CocoGeneratedFileWriter(StandardCharsets.UTF_8,
                (source, target) -> {
                    throw new IOException("simulated Windows lock");
                });

        assertThatThrownBy(() -> writer.write(output,
                CocoCodegenResult.of(List.of(new CocoGeneratedFile("existing.txt", "generated"))),
                new CocoGeneratedFileWriteOptions(true, false)))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("failed to write generated files");

        assertThat(Files.readString(existing)).isEqualTo("business-content");
        assertThat(CocoOutputRootLock.recoveryMarkerPath(output)).doesNotExist();
    }

    @Test
    void failsFastWhenSameCanonicalRootIsAlreadyLocked() throws Exception {
        Path output = this.tempDirectory.resolve("concurrent");
        CountDownLatch moveStarted = new CountDownLatch(1);
        CountDownLatch releaseMove = new CountDownLatch(1);
        CocoGeneratedFileWriter firstWriter = new CocoGeneratedFileWriter(StandardCharsets.UTF_8,
                (source, target) -> {
                    moveStarted.countDown();
                    try {
                        if (!releaseMove.await(10, TimeUnit.SECONDS)) {
                            throw new IOException("timed out waiting to release simulated move");
                        }
                    }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted while simulating move", ex);
                    }
                    Files.move(source, target);
                });
        CocoCodegenResult result = CocoCodegenResult.of(List.of(new CocoGeneratedFile("value.txt", "first")));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<List<Path>> first = executor.submit(() -> firstWriter.write(output, result));
            assertThat(moveStarted.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> new CocoGeneratedFileWriter().write(output, result))
                    .isInstanceOf(CocoCodegenException.class)
                    .hasMessageContaining("locked");

            releaseMove.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly(output.resolve("value.txt").toAbsolutePath());
        }
        finally {
            releaseMove.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reportsFileChannelLockContention() throws IOException {
        Path output = this.tempDirectory.resolve("file-lock");
        Path lockFile = CocoOutputRootLock.lockFilePath(output);
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            assertThatThrownBy(() -> new CocoGeneratedFileWriter().write(output,
                    CocoCodegenResult.of(List.of(new CocoGeneratedFile("value.txt", "value")))))
                    .isInstanceOf(CocoCodegenException.class)
                    .hasMessageContaining("lock");
        }
    }

    @Test
    void reportsLockHeldByAnotherJvmWithDifferentTempDirectories() throws Exception {
        Path output = this.tempDirectory.resolve("cross-process-lock");
        Path ready = this.tempDirectory.resolve("cross-process.ready");
        Path release = this.tempDirectory.resolve("cross-process.release");
        Path firstTemp = Files.createDirectories(this.tempDirectory.resolve("jvm-temp-a"));
        Path secondTemp = Files.createDirectories(this.tempDirectory.resolve("jvm-temp-b"));
        Process holder = startLockProcess(firstTemp, "hold", output.toString(),
                ready.toString(), release.toString());
        try {
            assertThat(awaitExists(ready, holder, 10, TimeUnit.SECONDS))
                    .withFailMessage(() -> readProcessOutput(holder))
                    .isTrue();

            Process contender = startLockProcess(secondTemp, "try", output.toString());
            assertThat(contender.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(contender.exitValue()).isEqualTo(2);
            assertThat(readProcessOutput(contender)).contains("locked by another process");
        }
        finally {
            Files.writeString(release, "release");
            if (!holder.waitFor(10, TimeUnit.SECONDS)) {
                holder.destroyForcibly();
                holder.waitFor(10, TimeUnit.SECONDS);
            }
        }
        assertThat(holder.exitValue()).isZero();
        assertThat(CocoOutputRootLock.lockFilePath(output)).exists();
    }

    @Test
    void recoveryMarkerIsVisibleToJvmWithDifferentTempDirectory() throws Exception {
        Path output = this.tempDirectory.resolve("cross-temp-recovery");
        Path foreignTemp = Files.createDirectories(this.tempDirectory.resolve("foreign-jvm-temp"));
        Path marker = CocoOutputRootLock.recoveryMarkerPath(output);
        Files.writeString(marker, "recovery-required");
        try {
            Process checker = startLockProcess(foreignTemp, "check-marker", output.toString());
            assertThat(checker.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(checker.exitValue()).isEqualTo(3);
            assertThat(readProcessOutput(checker)).contains("recovery is required");
            assertThat(marker).startsWith(output.resolve(".coco-codegen"));
        }
        finally {
            Files.deleteIfExists(marker);
        }
    }

    @Test
    void retainsRecoveryMarkerAndBackupWhenRollbackCannotFinish() throws IOException {
        Path output = this.tempDirectory.resolve("recovery-marker");
        Path existing = output.resolve("existing.txt");
        Files.createDirectories(output);
        Files.writeString(existing, "business-content");
        AtomicInteger moves = new AtomicInteger();
        CocoGeneratedFileWriter writer = new CocoGeneratedFileWriter(StandardCharsets.UTF_8,
                (source, target) -> {
                    int move = moves.incrementAndGet();
                    if (move == 3 || move == 4) {
                        throw new IOException("simulated commit and rollback failure");
                    }
                    Files.move(source, target);
                });
        CocoCodegenResult result = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile("existing.txt", "generated"),
                new CocoGeneratedFile("second.txt", "generated")));
        Path marker = CocoOutputRootLock.recoveryMarkerPath(output);

        try {
            assertThatThrownBy(() -> writer.write(output, result,
                    new CocoGeneratedFileWriteOptions(true, false)))
                    .isInstanceOf(CocoCodegenException.class)
                    .hasMessageContaining("recovery marker retained");
            assertThat(marker).exists();

            Properties recovery = loadProperties(marker);
            Path backup = Path.of(recovery.getProperty("file.0.backup"));
            assertThat(Files.readString(backup)).isEqualTo("business-content");
            assertThatThrownBy(() -> new CocoGeneratedFileWriter().write(output, result,
                    new CocoGeneratedFileWriteOptions(true, false)))
                    .isInstanceOf(CocoCodegenException.class)
                    .hasMessageContaining("recovery is required");

            Files.move(backup, existing);
        }
        finally {
            Files.deleteIfExists(marker);
        }
        assertThat(Files.readString(existing)).isEqualTo("business-content");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsRealWindowsJunctionInsideOutputRoot() throws Exception {
        Path output = this.tempDirectory.resolve("junction-output");
        Path outside = this.tempDirectory.resolve("junction-target");
        Path junction = output.resolve("linked");
        Files.createDirectories(output);
        Files.createDirectories(outside);
        Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J",
                junction.toString(), outside.toString())
                .redirectErrorStream(true)
                .start();
        String commandOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        Assumptions.assumeTrue(exitCode == 0, () -> "junction creation unavailable: " + commandOutput);

        try {
            assertThatThrownBy(() -> new CocoGeneratedFileWriter().write(output,
                    CocoCodegenResult.of(List.of(new CocoGeneratedFile("linked/escaped.txt", "value")))))
                    .isInstanceOf(CocoCodegenException.class)
                    .hasMessageMatching("(?s).*(reparse point|canonical output root).*?");
            assertThat(outside.resolve("escaped.txt")).doesNotExist();
        }
        finally {
            Files.deleteIfExists(junction);
        }
    }

    @Test
    void dryRunCreatesNoDirectoriesOrFiles() {
        Path output = this.tempDirectory.resolve("dry-run");
        CocoCodegenResult result = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile("nested/value.txt", "value")));

        List<Path> targets = new CocoGeneratedFileWriter().write(
                output, result, new CocoGeneratedFileWriteOptions(false, true));

        assertThat(targets).containsExactly(output.resolve("nested/value.txt").toAbsolutePath());
        assertThat(output).doesNotExist();
    }

    @Test
    void rejectsUnsafeAndDuplicatePaths() {
        CocoGeneratedFileWriter writer = new CocoGeneratedFileWriter();
        for (String path : List.of(
                "/absolute.txt",
                "../escape.txt",
                "C:\\escape.txt",
                "a//b.txt",
                "a/./b.txt",
                "generated/CON.java",
                "generated/name?.java",
                "generated/trailing./Sample.java")) {
            CocoCodegenResult result = CocoCodegenResult.of(List.of(new CocoGeneratedFile(path, "value")));
            assertThatThrownBy(() -> writer.write(this.tempDirectory, result,
                    new CocoGeneratedFileWriteOptions(false, true)))
                    .as(path)
                    .isInstanceOf(CocoCodegenException.class);
        }

        CocoCodegenResult duplicateResult = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile("same/file.txt", "one"),
                new CocoGeneratedFile("same\\file.txt", "two")));
        assertThatThrownBy(() -> writer.write(this.tempDirectory, duplicateResult,
                new CocoGeneratedFileWriteOptions(false, true)))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("duplicate generated output");

        CocoCodegenResult reservedState = CocoCodegenResult.of(List.of(
                new CocoGeneratedFile(".coco-codegen/owned.txt", "value")));
        assertThatThrownBy(() -> writer.write(this.tempDirectory, reservedState,
                new CocoGeneratedFileWriteOptions(false, true)))
                .isInstanceOf(CocoCodegenException.class)
                .hasMessageContaining("reserved for codegen state");
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static boolean awaitExists(Path path, Process process, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline && process.isAlive()) {
            if (Files.exists(path)) {
                return true;
            }
            Thread.sleep(25);
        }
        return Files.exists(path);
    }

    private static String readProcessOutput(Process process) {
        if (process.isAlive()) {
            return "lock helper process did not signal readiness";
        }
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            return ex.toString();
        }
    }

    private static Process startLockProcess(Path tempDirectory, String... arguments) throws IOException {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new java.util.ArrayList<>();
        command.add(javaExecutable);
        command.add("-Djava.io.tmpdir=" + tempDirectory);
        command.add("-cp");
        command.add(classpath);
        command.add(CocoOutputRootLockProcess.class.getName());
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }
}
