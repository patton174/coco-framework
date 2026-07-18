package io.github.coco.maven;

/**
 * Coco archive parser resource limits.
 */
record CocoArchiveLimits(
        long outerEntryBytes,
        long outerTotalBytes,
        long archiveReadBytes,
        long resolvedArtifactsBytes,
        long nestedEntryBytes,
        long nestedLibraryBytes,
        long nestedArchiveBytes,
        long pomPropertiesBytes,
        long manifestBytes,
        long indexBytes,
        int indexLineBytes,
        int layerCount,
        int entryCount,
        int entryNameBytes,
        int gavValueBytes,
        int yamlNestingDepth,
        long executablePrefixBytes) {

    static final CocoArchiveLimits DEFAULT = new CocoArchiveLimits(
            256L * 1024 * 1024,
            2L * 1024 * 1024 * 1024,
            8L * 1024 * 1024 * 1024,
            2L * 1024 * 1024 * 1024,
            64L * 1024 * 1024,
            512L * 1024 * 1024,
            2L * 1024 * 1024 * 1024,
            64L * 1024,
            1024L * 1024,
            8L * 1024 * 1024,
            4 * 1024,
            64,
            65_536,
            1024,
            512,
            16,
            16L * 1024 * 1024);

    CocoArchiveLimits {
        if (outerEntryBytes <= 0 || outerTotalBytes <= 0 || archiveReadBytes <= 0
                || resolvedArtifactsBytes <= 0 || nestedEntryBytes <= 0
                || nestedLibraryBytes <= 0 || nestedArchiveBytes <= 0 || pomPropertiesBytes <= 0
                || manifestBytes <= 0 || indexBytes <= 0 || indexLineBytes <= 0 || layerCount <= 0
                || entryCount <= 0 || entryNameBytes <= 0 || gavValueBytes <= 0
                || yamlNestingDepth <= 0 || executablePrefixBytes <= 0) {
            throw new IllegalArgumentException("Archive resource limits must be positive.");
        }
    }
}
