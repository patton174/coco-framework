package io.github.coco.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Fully executable ZIP 结构解析与 Zip64 回归测试。
 */
class CocoExecutableArchiveTest {

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int LOCAL_FILE_SIGNATURE = 0x04034b50;
    private static final long UINT32_MAX = 0xffff_ffffL;
    private static final byte[] LAUNCH_SCRIPT = "#!/bin/sh\n# PK\\003\\004 decoy\n".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDir;

    @Test
    void relocatesSfxArchiveWhenOnlyZip64EntryCountsUseSentinels() throws Exception {
        Path archive = this.tempDir.resolve("count-only-zip64.jar");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int index = 0; index < 65_535; index++) {
                output.putNextEntry(new ZipEntry("entries/" + index));
                output.closeEntry();
            }
        }
        byte[] zip = Files.readAllBytes(archive);
        int eocd = findEocd(zip);
        ByteBuffer end = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(Short.toUnsignedInt(end.getShort(eocd + 8))).isEqualTo(0xffff);
        assertThat(Short.toUnsignedInt(end.getShort(eocd + 10))).isEqualTo(0xffff);
        assertThat(Integer.toUnsignedLong(end.getInt(eocd + 12))).isNotEqualTo(UINT32_MAX);
        assertThat(Integer.toUnsignedLong(end.getInt(eocd + 16))).isNotEqualTo(UINT32_MAX);

        Files.write(archive, concat(LAUNCH_SCRIPT, zip));
        CocoExecutableArchive.relocateOffsets(archive, LAUNCH_SCRIPT.length);

        assertThat(CocoExecutableArchive.readPrefix(archive)).isEqualTo(LAUNCH_SCRIPT);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            assertThat(zipFile.size()).isEqualTo(65_535);
            assertThat(zipFile.getEntry("entries/0")).isNotNull();
            assertThat(zipFile.getEntry("entries/65534")).isNotNull();
        }
    }

    @Test
    void recognizesCountOnlyZip64EndRecordsWithSmallCentralDirectory() throws Exception {
        Path archive = countOnlyZip64Archive(this.tempDir.resolve("synthetic-count-only.jar"));

        assertThat(CocoExecutableArchive.readPrefix(archive)).isEqualTo(LAUNCH_SCRIPT);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            assertThat(zipFile.size()).isOne();
            assertThat(zipFile.getEntry("entry")).isNotNull();
        }
    }

    @ParameterizedTest
    @EnumSource(MultiDiskCorruption.class)
    void rejectsMultiDiskFieldsFailClosed(MultiDiskCorruption corruption) throws Exception {
        Path archive = countOnlyZip64Archive(this.tempDir.resolve(corruption.name() + ".jar"));
        byte[] bytes = Files.readAllBytes(archive);
        StructureOffsets offsets = structureOffsets(bytes);
        corruption.apply(bytes, offsets);
        Files.write(archive, bytes);

        assertThatThrownBy(() -> CocoExecutableArchive.readPrefix(archive))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Multi-disk ZIP archives are not supported");
        assertThat(Files.readAllBytes(archive)).isEqualTo(bytes);
    }

    @Test
    void readsZip64LocalOffsetAfterPrecedingExtraAndSizeValues() throws Exception {
        Path archive = this.tempDir.resolve("zip64-extra-order.jar");
        byte[] zip = zipWithOrderedZip64Extra();
        Files.write(archive, concat(LAUNCH_SCRIPT, zip));

        CocoExecutableArchive.relocateOffsets(archive, LAUNCH_SCRIPT.length);

        assertThat(CocoExecutableArchive.readPrefix(archive)).isEqualTo(LAUNCH_SCRIPT);
        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipEntry entry = zipFile.getEntry("a");
            assertThat(entry).isNotNull();
            assertThat(entry.getSize()).isZero();
        }
    }

    private Path countOnlyZip64Archive(Path archive) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("entry"));
            output.closeEntry();
        }
        byte[] countOnlyZip64 = addCountOnlyZip64End(bytes.toByteArray());
        Files.write(archive, concat(LAUNCH_SCRIPT, countOnlyZip64));
        CocoExecutableArchive.relocateOffsets(archive, LAUNCH_SCRIPT.length);
        return archive;
    }

    private byte[] addCountOnlyZip64End(byte[] zip) {
        int eocd = findEocd(zip);
        ByteBuffer source = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        long centralDirectorySize = Integer.toUnsignedLong(source.getInt(eocd + 12));
        long centralDirectoryOffset = Integer.toUnsignedLong(source.getInt(eocd + 16));
        ByteArrayOutputStream output = new ByteArrayOutputStream(zip.length + 76);
        output.writeBytes(Arrays.copyOf(zip, eocd));
        writeInt(output, 0x06064b50);
        writeLong(output, 44);
        writeShort(output, 45);
        writeShort(output, 45);
        writeInt(output, 0);
        writeInt(output, 0);
        writeLong(output, 1);
        writeLong(output, 1);
        writeLong(output, centralDirectorySize);
        writeLong(output, centralDirectoryOffset);
        writeInt(output, 0x07064b50);
        writeInt(output, 0);
        writeLong(output, eocd);
        writeInt(output, 1);
        byte[] patchedEocd = Arrays.copyOfRange(zip, eocd, zip.length);
        ByteBuffer end = ByteBuffer.wrap(patchedEocd).order(ByteOrder.LITTLE_ENDIAN);
        end.putShort(8, (short) 0xffff);
        end.putShort(10, (short) 0xffff);
        output.writeBytes(patchedEocd);
        return output.toByteArray();
    }

    private byte[] zipWithOrderedZip64Extra() {
        byte[] name = {'a'};
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeInt(output, LOCAL_FILE_SIGNATURE);
        writeShort(output, 45);
        writeShort(output, 0);
        writeShort(output, ZipEntry.STORED);
        writeShort(output, 0);
        writeShort(output, 0);
        writeInt(output, 0);
        writeInt(output, 0);
        writeInt(output, 0);
        writeShort(output, name.length);
        writeShort(output, 0);
        output.writeBytes(name);

        int centralDirectoryOffset = output.size();
        ByteArrayOutputStream extra = new ByteArrayOutputStream();
        writeShort(extra, 0xcafe);
        writeShort(extra, 3);
        extra.writeBytes(new byte[] {1, 2, 3});
        writeShort(extra, 0x0001);
        writeShort(extra, 24);
        writeLong(extra, 0);
        writeLong(extra, 0);
        writeLong(extra, 0);
        byte[] extraBytes = extra.toByteArray();

        writeInt(output, CENTRAL_DIRECTORY_SIGNATURE);
        writeShort(output, 45);
        writeShort(output, 45);
        writeShort(output, 0);
        writeShort(output, ZipEntry.STORED);
        writeShort(output, 0);
        writeShort(output, 0);
        writeInt(output, 0);
        writeInt(output, UINT32_MAX);
        writeInt(output, UINT32_MAX);
        writeShort(output, name.length);
        writeShort(output, extraBytes.length);
        writeShort(output, 0);
        writeShort(output, 0);
        writeShort(output, 0);
        writeInt(output, 0);
        writeInt(output, UINT32_MAX);
        output.writeBytes(name);
        output.writeBytes(extraBytes);

        int centralDirectorySize = output.size() - centralDirectoryOffset;
        writeInt(output, EOCD_SIGNATURE);
        writeShort(output, 0);
        writeShort(output, 0);
        writeShort(output, 1);
        writeShort(output, 1);
        writeInt(output, centralDirectorySize);
        writeInt(output, centralDirectoryOffset);
        writeShort(output, 0);
        return output.toByteArray();
    }

    private StructureOffsets structureOffsets(byte[] bytes) {
        int eocd = findEocd(bytes);
        int locator = eocd - 20;
        int zip64Eocd = locator - 56;
        long centralDirectory = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(zip64Eocd + 48);
        return new StructureOffsets(eocd, locator, zip64Eocd, Math.toIntExact(centralDirectory));
    }

    private static int findEocd(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = bytes.length - 22; index >= 0; index--) {
            if (buffer.getInt(index) == EOCD_SIGNATURE
                    && index + 22 + Short.toUnsignedInt(buffer.getShort(index + 20)) == bytes.length) {
                return index;
            }
        }
        throw new IllegalArgumentException("EOCD not found");
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void writeShort(ByteArrayOutputStream output, long value) {
        output.write((int) value & 0xff);
        output.write((int) (value >>> 8) & 0xff);
    }

    private static void writeInt(ByteArrayOutputStream output, long value) {
        writeShort(output, value);
        writeShort(output, value >>> 16);
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        writeInt(output, value);
        writeInt(output, value >>> 32);
    }

    private record StructureOffsets(int eocd, int locator, int zip64Eocd, int centralDirectory) {
    }

    private enum MultiDiskCorruption {
        EOCD_DISK {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putShort(offsets.eocd() + 4, (short) 1);
            }
        },
        EOCD_CENTRAL_DIRECTORY_DISK {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putShort(offsets.eocd() + 6, (short) 1);
            }
        },
        EOCD_PER_DISK_ENTRY_COUNT {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putShort(offsets.eocd() + 8, (short) 1);
                buffer.putShort(offsets.eocd() + 10, (short) 2);
            }
        },
        LOCATOR_DISK {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putInt(offsets.locator() + 4, 1);
            }
        },
        LOCATOR_TOTAL_DISKS {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putInt(offsets.locator() + 16, 2);
            }
        },
        ZIP64_EOCD_DISK {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putInt(offsets.zip64Eocd() + 16, 1);
            }
        },
        ZIP64_EOCD_CENTRAL_DIRECTORY_DISK {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putInt(offsets.zip64Eocd() + 20, 1);
            }
        },
        ZIP64_EOCD_PER_DISK_ENTRY_COUNT {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putLong(offsets.zip64Eocd() + 24, 2);
            }
        },
        CENTRAL_DIRECTORY_ENTRY_DISK {
            @Override
            void apply(ByteBuffer buffer, StructureOffsets offsets) {
                buffer.putShort(offsets.centralDirectory() + 34, (short) 1);
            }
        };

        final void apply(byte[] bytes, StructureOffsets offsets) {
            apply(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN), offsets);
        }

        abstract void apply(ByteBuffer buffer, StructureOffsets offsets);
    }
}
