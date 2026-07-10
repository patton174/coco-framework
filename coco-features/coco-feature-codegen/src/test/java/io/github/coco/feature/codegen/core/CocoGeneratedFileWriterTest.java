package io.github.coco.feature.codegen.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
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
    }
}
