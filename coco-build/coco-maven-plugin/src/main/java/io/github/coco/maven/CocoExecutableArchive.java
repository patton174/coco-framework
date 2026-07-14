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
        try (FileChannel channel = FileChannel.open(archivePath, StandardOpenOption.READ)) {
            ZipLayout layout = ZipLayout.read(channel, 0);
            if (layout.firstLocalHeaderOffset() == 0) {
                return new byte[0];
            }
            if (layout.firstLocalHeaderOffset() > Integer.MAX_VALUE) {
                throw new IOException("Executable archive launch script is too large.");
            }
            ByteBuffer prefix = ByteBuffer.allocate((int) layout.firstLocalHeaderOffset());
            readFully(channel, prefix, 0);
            return prefix.array();
        }
    }

    static void relocateOffsets(Path archivePath, long zipStart) throws IOException {
        if (zipStart == 0) {
            return;
        }
        try (FileChannel channel = FileChannel.open(archivePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ZipLayout layout = ZipLayout.read(channel, zipStart);
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

    private static void readFully(FileChannel channel, ByteBuffer target, long position) throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target, position + target.position());
            if (read < 0) {
                throw new IOException("Truncated ZIP archive.");
            }
        }
    }

    private static long unsignedShort(FileChannel channel, long position) throws IOException {
        return readUnsigned(channel, position, Short.BYTES);
    }

    private static long unsignedInt(FileChannel channel, long position) throws IOException {
        return readUnsigned(channel, position, Integer.BYTES);
    }

    private static long unsignedLong(FileChannel channel, long position) throws IOException {
        return readUnsigned(channel, position, Long.BYTES);
    }

    private static long readUnsigned(FileChannel channel, long position, int width) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(width).order(ByteOrder.LITTLE_ENDIAN);
        readFully(channel, buffer, position);
        buffer.flip();
        return switch (width) {
            case Short.BYTES -> Short.toUnsignedLong(buffer.getShort());
            case Integer.BYTES -> Integer.toUnsignedLong(buffer.getInt());
            case Long.BYTES -> buffer.getLong();
            default -> throw new IllegalArgumentException("Unsupported ZIP field width: " + width);
        };
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

        static ZipLayout read(FileChannel channel, long zipStart) throws IOException {
            long fileSize = channel.size();
            long eocd = findEocd(channel, fileSize);
            long eocdDisk = unsignedShort(channel, eocd + 4);
            long eocdCentralDirectoryDisk = unsignedShort(channel, eocd + 6);
            long eocdEntriesOnDisk = unsignedShort(channel, eocd + 8);
            long eocdTotalEntries = unsignedShort(channel, eocd + 10);
            long centralDirectoryOffset = unsignedInt(channel, eocd + 16);
            long centralDirectorySize = unsignedInt(channel, eocd + 12);
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
                if (locator < zipStart || unsignedInt(channel, locator) != ZIP64_LOCATOR_SIGNATURE) {
                    throw new IOException("ZIP64 end-of-central-directory locator is missing.");
                }
                long zip64EocdDisk = unsignedInt(channel, locator + 4);
                long totalDisks = unsignedInt(channel, locator + 16);
                if (zip64EocdDisk != 0 || totalDisks != 1) {
                    throw multiDisk("ZIP64 locator references multiple disks");
                }
                long zip64EocdRelativeOffset = unsignedLong(channel, locator + 8);
                long zip64Eocd = zipStart + zip64EocdRelativeOffset;
                if (zip64Eocd < zipStart || zip64Eocd + 56 > locator
                        || unsignedInt(channel, zip64Eocd) != ZIP64_EOCD_SIGNATURE) {
                    throw new IOException("Invalid ZIP64 end-of-central-directory record.");
                }
                long zip64RecordSize = unsignedLong(channel, zip64Eocd + 4);
                if (zip64RecordSize < 44 || zip64RecordSize > locator - zip64Eocd - 12
                        || zip64Eocd + 12 + zip64RecordSize != locator) {
                    throw new IOException("Invalid ZIP64 end-of-central-directory record size.");
                }
                long zip64Disk = unsignedInt(channel, zip64Eocd + 16);
                long zip64CentralDirectoryDisk = unsignedInt(channel, zip64Eocd + 20);
                requireSingleDisk(zip64Disk, zip64CentralDirectoryDisk,
                        "ZIP64 end-of-central-directory record");
                long zip64EntriesOnDisk = unsignedLong(channel, zip64Eocd + 24);
                long zip64TotalEntries = unsignedLong(channel, zip64Eocd + 32);
                if (zip64EntriesOnDisk < 0 || zip64TotalEntries < 0
                        || zip64EntriesOnDisk != zip64TotalEntries) {
                    throw multiDisk("ZIP64 end-of-central-directory entry counts differ");
                }
                centralDirectorySize = unsignedLong(channel, zip64Eocd + 40);
                centralDirectoryOffset = unsignedLong(channel, zip64Eocd + 48);
                requireMatchingEocdValue(eocdEntriesOnDisk, UINT16_MAX, zip64EntriesOnDisk,
                        "entries on this disk");
                requireMatchingEocdValue(eocdTotalEntries, UINT16_MAX, zip64TotalEntries,
                        "total entries");
                requireMatchingEocdValue(unsignedInt(channel, eocd + 12), UINT32_MAX,
                        centralDirectorySize, "central-directory size");
                requireMatchingEocdValue(unsignedInt(channel, eocd + 16), UINT32_MAX,
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
                if (end - cursor < 46 || unsignedInt(channel, cursor) != CENTRAL_DIRECTORY_SIGNATURE) {
                    throw new IOException("Invalid ZIP central-directory entry.");
                }
                if (unsignedShort(channel, cursor + 34) != 0) {
                    throw multiDisk("ZIP central-directory entry references another disk");
                }
                int nameLength = Math.toIntExact(unsignedShort(channel, cursor + 28));
                int extraLength = Math.toIntExact(unsignedShort(channel, cursor + 30));
                int commentLength = Math.toIntExact(unsignedShort(channel, cursor + 32));
                long entryEnd = cursor + 46L + nameLength + extraLength + commentLength;
                if (entryEnd > end) {
                    throw new IOException("Truncated ZIP central-directory entry.");
                }
                long localOffset = unsignedInt(channel, cursor + 42);
                OffsetField localOffsetField = new OffsetField(cursor + 42, localOffset, Integer.BYTES);
                if (localOffset == UINT32_MAX) {
                    localOffsetField = zip64LocalHeaderOffset(channel, cursor + 46L + nameLength, extraLength, cursor);
                    localOffset = localOffsetField.value();
                }
                long localHeader = zipStart + localOffset;
                if (localHeader < zipStart || localHeader + 4 > centralDirectory
                        || unsignedInt(channel, localHeader) != LOCAL_FILE_SIGNATURE) {
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

        private static OffsetField zip64LocalHeaderOffset(FileChannel channel, long extraStart, int extraLength,
                long centralDirectoryEntry) throws IOException {
            long cursor = extraStart;
            long end = extraStart + extraLength;
            boolean needsUncompressed = unsignedInt(channel, centralDirectoryEntry + 24) == UINT32_MAX;
            boolean needsCompressed = unsignedInt(channel, centralDirectoryEntry + 20) == UINT32_MAX;
            while (cursor + 4 <= end) {
                int id = Math.toIntExact(unsignedShort(channel, cursor));
                int size = Math.toIntExact(unsignedShort(channel, cursor + 2));
                long data = cursor + 4;
                if (data + size > end) {
                    throw new IOException("Truncated ZIP extra field.");
                }
                if (id == 0x0001) {
                    long value = data;
                    if (needsUncompressed) {
                        value += Long.BYTES;
                    }
                    if (needsCompressed) {
                        value += Long.BYTES;
                    }
                    if (value + Long.BYTES > data + size) {
                        throw new IOException("ZIP64 local-file-header offset is missing.");
                    }
                    return new OffsetField(value, unsignedLong(channel, value), Long.BYTES);
                }
                cursor = data + size;
            }
            throw new IOException("ZIP64 extra field is missing local-file-header offset.");
        }

        private static long findEocd(FileChannel channel, long fileSize) throws IOException {
            long start = Math.max(0, fileSize - EOCD_MIN_SIZE - 0xffffL);
            for (long candidate = fileSize - EOCD_MIN_SIZE; candidate >= start; candidate--) {
                if (unsignedInt(channel, candidate) == EOCD_SIGNATURE
                        && candidate + EOCD_MIN_SIZE + unsignedShort(channel, candidate + 20) == fileSize) {
                    return candidate;
                }
            }
            throw new IOException("ZIP end-of-central-directory record is missing.");
        }
    }
}
