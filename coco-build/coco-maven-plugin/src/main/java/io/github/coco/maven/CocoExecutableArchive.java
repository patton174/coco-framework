package io.github.coco.maven;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Boot fully executable ZIP archive helpers.
 *
 * <p>The launch script is an SFX prefix. ZIP offsets include that prefix, so a
 * rewritten archive must relocate its central-directory offsets before it can
 * be read by {@link java.util.jar.JarFile}.</p>
 */
final class CocoExecutableArchive {

    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int ZIP64_EOCD_SIGNATURE = 0x06064b50;
    private static final int ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
    private static final int CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
    private static final int LOCAL_FILE_SIGNATURE = 0x04034b50;
    private static final int EOCD_MIN_SIZE = 22;
    private static final int ZIP64_LOCATOR_SIZE = 20;
    private static final long UINT16_MAX = 0xffffL;
    private static final long UINT32_MAX = 0xffff_ffffL;

    private CocoExecutableArchive() {
    }

    static byte[] readPrefix(Path archivePath) throws IOException {
        return readPrefix(archivePath, Integer.MAX_VALUE,
                new CocoArchiveIo.CumulativeBudget(Long.MAX_VALUE, "Executable archive reads"));
    }

    static byte[] readPrefix(Path archivePath, long maximumPrefixBytes,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        try (FileChannel channel = FileChannel.open(archivePath, StandardOpenOption.READ)) {
            ZipLayout layout = ZipLayout.read(channel, 0, readBudget);
            if (layout.firstLocalHeaderOffset() == 0) {
                return new byte[0];
            }
            if (layout.firstLocalHeaderOffset() > maximumPrefixBytes) {
                throw new IOException("Executable archive prefix exceeds byte limit "
                        + maximumPrefixBytes + ".");
            }
            if (layout.firstLocalHeaderOffset() > Integer.MAX_VALUE) {
                throw new IOException("Executable archive launch script is too large.");
            }
            ByteBuffer prefix = ByteBuffer.allocate((int) layout.firstLocalHeaderOffset());
            readFully(channel, prefix, 0, readBudget);
            return prefix.array();
        }
    }

    static void relocateOffsets(Path archivePath, long zipStart) throws IOException {
        relocateOffsets(archivePath, zipStart,
                new CocoArchiveIo.CumulativeBudget(Long.MAX_VALUE, "Executable archive reads"));
    }

    static void relocateOffsets(Path archivePath, long zipStart,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        if (zipStart == 0) {
            return;
        }
        try (FileChannel channel = FileChannel.open(archivePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ZipLayout layout = ZipLayout.read(channel, zipStart, readBudget);
            for (OffsetField field : layout.localHeaderOffsets()) {
                addOffset(channel, field, zipStart);
            }
            for (OffsetField field : layout.centralDirectoryOffsets()) {
                addOffset(channel, field, zipStart);
            }
            if (layout.zip64EocdOffset() != null) {
                addOffset(channel, layout.zip64EocdOffset(), zipStart);
            }
        }
    }

    private static void addOffset(FileChannel channel, OffsetField field, long delta) throws IOException {
        if (field.value() > Long.MAX_VALUE - delta) {
            throw new IOException("ZIP offset overflow while preserving executable archive prefix.");
        }
        long value = field.value() + delta;
        if (field.width() == Integer.BYTES && value > UINT32_MAX) {
            throw new IOException("ZIP32 offset overflow while preserving executable archive prefix.");
        }
        writeUnsigned(channel, field.position(), value, field.width());
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long position,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, position + target.position());
            if (read < 0) {
                throw new IOException("Truncated ZIP archive.");
            }
            if (read == 0) {
                throw new IOException("Unable to make progress while reading ZIP archive.");
            }
            readBudget.consume(read);
        }
    }

    private static long unsignedShort(FileChannel channel, long position,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        return readUnsigned(channel, position, Short.BYTES, readBudget);
    }

    private static long unsignedInt(FileChannel channel, long position,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        return readUnsigned(channel, position, Integer.BYTES, readBudget);
    }

    private static long unsignedLong(FileChannel channel, long position,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        return readUnsigned(channel, position, Long.BYTES, readBudget);
    }

    private static long readUnsigned(FileChannel channel, long position, int width,
            CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(width).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, buffer, position, readBudget);
        buffer.flip();
        return switch (width) {
            case Short.BYTES -> Short.toUnsignedLong(buffer.getShort());
            case Integer.BYTES -> Integer.toUnsignedLong(buffer.getInt());
            case Long.BYTES -> checkedUnsignedLong(buffer.getLong());
            default -> throw new IllegalArgumentException("Unsupported ZIP field width: " + width);
        };
    }

    private static long checkedUnsignedLong(long value) throws IOException {
        if (value < 0) {
            throw new IOException("ZIP64 unsigned value exceeds the supported range.");
        }
        return value;
    }

    private static void writeUnsigned(FileChannel channel, long position, long value, int width) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(width).order(ByteOrder.LITTLE_ENDIAN);
        if (width == Integer.BYTES) {
            buffer.putInt((int) value);
        }
        else if (width == Long.BYTES) {
            buffer.putLong(value);
        }
        else {
            throw new IllegalArgumentException("Unsupported ZIP field width: " + width);
        }
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer, position + buffer.position());
        }
    }

    private record OffsetField(long position, long value, int width) {
    }

    private record ZipLayout(long firstLocalHeaderOffset, List<OffsetField> localHeaderOffsets,
            List<OffsetField> centralDirectoryOffsets, OffsetField zip64EocdOffset) {

        static ZipLayout read(FileChannel channel, long zipStart,
                CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
            long fileSize = channel.size();
            long eocd = findEocd(channel, fileSize, readBudget);
            long eocdDisk = unsignedShort(channel, eocd + 4, readBudget);
            long eocdCentralDirectoryDisk = unsignedShort(channel, eocd + 6, readBudget);
            long eocdEntriesOnDisk = unsignedShort(channel, eocd + 8, readBudget);
            long eocdTotalEntries = unsignedShort(channel, eocd + 10, readBudget);
            long centralDirectoryOffset = unsignedInt(channel, eocd + 16, readBudget);
            long centralDirectorySize = unsignedInt(channel, eocd + 12, readBudget);
            requireSingleDisk(eocdDisk, eocdCentralDirectoryDisk, "ZIP end-of-central-directory record");
            if (eocdEntriesOnDisk != UINT16_MAX && eocdTotalEntries != UINT16_MAX
                    && eocdEntriesOnDisk != eocdTotalEntries) {
                throw multiDisk("ZIP end-of-central-directory entry counts differ");
            }
            List<OffsetField> centralDirectoryOffsetFields = new ArrayList<>();
            centralDirectoryOffsetFields.add(new OffsetField(eocd + 16, centralDirectoryOffset, Integer.BYTES));
            OffsetField zip64EocdOffsetField = null;
            long expectedEntryCount = eocdTotalEntries;
            long centralDirectoryLimit = eocd;
            boolean usesZip64 = eocdEntriesOnDisk == UINT16_MAX || eocdTotalEntries == UINT16_MAX
                    || centralDirectoryOffset == UINT32_MAX || centralDirectorySize == UINT32_MAX;
            if (usesZip64) {
                long locator = eocd - ZIP64_LOCATOR_SIZE;
                if (locator < zipStart
                        || unsignedInt(channel, locator, readBudget) != ZIP64_LOCATOR_SIGNATURE) {
                    throw new IOException("ZIP64 end-of-central-directory locator is missing.");
                }
                long zip64EocdDisk = unsignedInt(channel, locator + 4, readBudget);
                long totalDisks = unsignedInt(channel, locator + 16, readBudget);
                if (zip64EocdDisk != 0 || totalDisks != 1) {
                    throw multiDisk("ZIP64 locator references multiple disks");
                }
                long zip64EocdRelativeOffset = unsignedLong(channel, locator + 8, readBudget);
                long zip64Eocd = zipStart + zip64EocdRelativeOffset;
                if (zip64Eocd < zipStart || zip64Eocd + 56 > locator
                        || unsignedInt(channel, zip64Eocd, readBudget) != ZIP64_EOCD_SIGNATURE) {
                    throw new IOException("Invalid ZIP64 end-of-central-directory record.");
                }
                long zip64RecordSize = unsignedLong(channel, zip64Eocd + 4, readBudget);
                if (zip64RecordSize < 44 || zip64RecordSize > locator - zip64Eocd - 12
                        || zip64Eocd + 12 + zip64RecordSize != locator) {
                    throw new IOException("Invalid ZIP64 end-of-central-directory record size.");
                }
                long zip64Disk = unsignedInt(channel, zip64Eocd + 16, readBudget);
                long zip64CentralDirectoryDisk = unsignedInt(channel, zip64Eocd + 20, readBudget);
                requireSingleDisk(zip64Disk, zip64CentralDirectoryDisk,
                        "ZIP64 end-of-central-directory record");
                long zip64EntriesOnDisk = unsignedLong(channel, zip64Eocd + 24, readBudget);
                long zip64TotalEntries = unsignedLong(channel, zip64Eocd + 32, readBudget);
                if (zip64EntriesOnDisk < 0 || zip64TotalEntries < 0
                        || zip64EntriesOnDisk != zip64TotalEntries) {
                    throw multiDisk("ZIP64 end-of-central-directory entry counts differ");
                }
                centralDirectorySize = unsignedLong(channel, zip64Eocd + 40, readBudget);
                centralDirectoryOffset = unsignedLong(channel, zip64Eocd + 48, readBudget);
                requireMatchingEocdValue(eocdEntriesOnDisk, UINT16_MAX, zip64EntriesOnDisk,
                        "entries on this disk");
                requireMatchingEocdValue(eocdTotalEntries, UINT16_MAX, zip64TotalEntries,
                        "total entries");
                requireMatchingEocdValue(unsignedInt(channel, eocd + 12, readBudget), UINT32_MAX,
                        centralDirectorySize, "central-directory size");
                requireMatchingEocdValue(unsignedInt(channel, eocd + 16, readBudget), UINT32_MAX,
                        centralDirectoryOffset, "central-directory offset");
                expectedEntryCount = zip64TotalEntries;
                centralDirectoryLimit = zip64Eocd;
                if (centralDirectoryOffsetFields.get(0).value() == UINT32_MAX) {
                    centralDirectoryOffsetFields.clear();
                }
                centralDirectoryOffsetFields.add(
                        new OffsetField(zip64Eocd + 48, centralDirectoryOffset, Long.BYTES));
                zip64EocdOffsetField = new OffsetField(locator + 8, zip64EocdRelativeOffset, Long.BYTES);
            }
            long centralDirectory = zipStart + centralDirectoryOffset;
            if (centralDirectoryOffset < 0 || centralDirectorySize < 0 || centralDirectory < zipStart
                    || centralDirectory > centralDirectoryLimit
                    || centralDirectorySize > centralDirectoryLimit - centralDirectory) {
                throw new IOException("Invalid ZIP central-directory bounds.");
            }
            List<OffsetField> localHeaderOffsets = new ArrayList<>();
            long cursor = centralDirectory;
            long end = centralDirectory + centralDirectorySize;
            long firstLocalHeaderOffset = Long.MAX_VALUE;
            long entryCount = 0;
            while (cursor < end) {
                if (end - cursor < 46
                        || unsignedInt(channel, cursor, readBudget) != CENTRAL_DIRECTORY_SIGNATURE) {
                    throw new IOException("Invalid ZIP central-directory entry.");
                }
                long diskStart = unsignedShort(channel, cursor + 34, readBudget);
                if (diskStart != 0 && diskStart != UINT16_MAX) {
                    throw multiDisk("ZIP central-directory entry references another disk");
                }
                int nameLength = Math.toIntExact(unsignedShort(channel, cursor + 28, readBudget));
                int extraLength = Math.toIntExact(unsignedShort(channel, cursor + 30, readBudget));
                int commentLength = Math.toIntExact(unsignedShort(channel, cursor + 32, readBudget));
                long entryEnd = cursor + 46L + nameLength + extraLength + commentLength;
                if (entryEnd > end) {
                    throw new IOException("Truncated ZIP central-directory entry.");
                }
                long localOffset = unsignedInt(channel, cursor + 42, readBudget);
                OffsetField localOffsetField = new OffsetField(cursor + 42, localOffset, Integer.BYTES);
                OffsetField zip64OffsetField = validateZip64Extra(channel,
                        cursor + 46L + nameLength, extraLength, cursor, readBudget);
                if (localOffset == UINT32_MAX) {
                    localOffsetField = zip64OffsetField;
                    localOffset = localOffsetField.value();
                }
                long localHeader = zipStart + localOffset;
                if (localHeader < zipStart || localHeader + 4 > centralDirectory
                        || unsignedInt(channel, localHeader, readBudget) != LOCAL_FILE_SIGNATURE) {
                    throw new IOException("Invalid ZIP local-file-header offset.");
                }
                localHeaderOffsets.add(localOffsetField);
                firstLocalHeaderOffset = Math.min(firstLocalHeaderOffset, localOffset);
                entryCount++;
                cursor = entryEnd;
            }
            if (cursor != end || localHeaderOffsets.isEmpty()) {
                throw new IOException("ZIP archive has no valid central-directory entries.");
            }
            if (entryCount != expectedEntryCount) {
                throw new IOException("ZIP central-directory entry count does not match the end record: expected "
                        + expectedEntryCount + " but found " + entryCount + ".");
            }
            return new ZipLayout(firstLocalHeaderOffset, List.copyOf(localHeaderOffsets),
                    List.copyOf(centralDirectoryOffsetFields), zip64EocdOffsetField);
        }

        private static void requireSingleDisk(long disk, long centralDirectoryDisk, String structure)
                throws IOException {
            if (disk != 0 || centralDirectoryDisk != 0) {
                throw multiDisk(structure + " has a non-zero disk number");
            }
        }

        private static void requireMatchingEocdValue(long eocdValue, long sentinel, long zip64Value,
                String field) throws IOException {
            if (eocdValue != sentinel && eocdValue != zip64Value) {
                throw new IOException("ZIP64 " + field + " does not match the ZIP end-of-central-directory record.");
            }
        }

        private static IOException multiDisk(String detail) {
            return new IOException("Multi-disk ZIP archives are not supported: " + detail + ".");
        }

        private static OffsetField validateZip64Extra(FileChannel channel, long extraStart, int extraLength,
                long centralDirectoryEntry, CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
            long cursor = extraStart;
            long end = extraStart + extraLength;
            if (end < extraStart) {
                throw new IOException("ZIP extra-field bounds overflow.");
            }
            boolean needsUncompressed = unsignedInt(
                    channel, centralDirectoryEntry + 24, readBudget) == UINT32_MAX;
            boolean needsCompressed = unsignedInt(
                    channel, centralDirectoryEntry + 20, readBudget) == UINT32_MAX;
            boolean needsOffset = unsignedInt(
                    channel, centralDirectoryEntry + 42, readBudget) == UINT32_MAX;
            boolean needsDisk = unsignedShort(
                    channel, centralDirectoryEntry + 34, readBudget) == UINT16_MAX;
            boolean needsZip64 = needsUncompressed || needsCompressed || needsOffset || needsDisk;
            OffsetField offsetField = null;
            boolean foundZip64 = false;
            while (cursor + 4 <= end) {
                int id = Math.toIntExact(unsignedShort(channel, cursor, readBudget));
                int size = Math.toIntExact(unsignedShort(channel, cursor + 2, readBudget));
                long data = cursor + 4;
                if (data + size < data || data + size > end) {
                    throw new IOException("Truncated ZIP extra field.");
                }
                if (id == 0x0001) {
                    if (foundZip64) {
                        throw new IOException("Duplicate ZIP64 extra field.");
                    }
                    foundZip64 = true;
                    int expectedSize = (needsUncompressed ? Long.BYTES : 0)
                            + (needsCompressed ? Long.BYTES : 0)
                            + (needsOffset ? Long.BYTES : 0)
                            + (needsDisk ? Integer.BYTES : 0);
                    if (!needsZip64 || size != expectedSize) {
                        throw new IOException("Inconsistent ZIP64 extra field.");
                    }
                    long value = data;
                    if (needsUncompressed) {
                        unsignedLong(channel, value, readBudget);
                        value += Long.BYTES;
                    }
                    if (needsCompressed) {
                        unsignedLong(channel, value, readBudget);
                        value += Long.BYTES;
                    }
                    if (needsOffset) {
                        offsetField = new OffsetField(
                                value, unsignedLong(channel, value, readBudget), Long.BYTES);
                        value += Long.BYTES;
                    }
                    if (needsDisk && unsignedInt(channel, value, readBudget) != 0) {
                        throw multiDisk("ZIP64 central-directory entry references another disk");
                    }
                }
                cursor = data + size;
            }
            if (cursor != end) {
                throw new IOException("Truncated ZIP extra field header.");
            }
            if (needsZip64 && !foundZip64) {
                throw new IOException("ZIP64 extra field is missing required values.");
            }
            return offsetField;
        }

        private static long findEocd(FileChannel channel, long fileSize,
                CocoArchiveIo.CumulativeBudget readBudget) throws IOException {
            long start = Math.max(0, fileSize - EOCD_MIN_SIZE - 0xffffL);
            for (long candidate = fileSize - EOCD_MIN_SIZE; candidate >= start; candidate--) {
                if (unsignedInt(channel, candidate, readBudget) == EOCD_SIGNATURE
                        && candidate + EOCD_MIN_SIZE
                                + unsignedShort(channel, candidate + 20, readBudget) == fileSize) {
                    return candidate;
                }
            }
            throw new IOException("ZIP end-of-central-directory record is missing.");
        }
    }
}
