package io.github.coco.maven;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Bounded archive stream operations shared by archive inspection and rewriting.
 */
final class CocoArchiveIo {

    private CocoArchiveIo() {
    }

    static byte[] readBounded(InputStream inputStream, long limit, String description) throws IOException {
        return readBounded(inputStream, limit, description, new CumulativeBudget(limit, description));
    }

    static byte[] readBounded(InputStream inputStream, long limit, String description,
            CumulativeBudget budget) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        copyBounded(inputStream, outputStream, limit, description, budget);
        return outputStream.toByteArray();
    }

    static long drainBounded(InputStream inputStream, long limit, String description) throws IOException {
        return drainBounded(inputStream, limit, description, new CumulativeBudget(limit, description));
    }

    static long drainBounded(InputStream inputStream, long limit, String description,
            CumulativeBudget budget) throws IOException {
        return copyBounded(inputStream, OutputStream.nullOutputStream(), limit, description, budget);
    }

    static long copyBounded(InputStream inputStream, OutputStream outputStream, long limit,
            String description) throws IOException {
        return copyBounded(inputStream, outputStream, limit, description,
                new CumulativeBudget(limit, description));
    }

    static long copyBounded(InputStream inputStream, OutputStream outputStream, long limit,
            String description, CumulativeBudget budget) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            total = addBounded(total, read, limit, description + " bytes");
            budget.consume(read);
            outputStream.write(buffer, 0, read);
        }
        return total;
    }

    static String sha256Bounded(InputStream inputStream, long limit, String description) throws IOException {
        return sha256Bounded(inputStream, limit, description, new CumulativeBudget(limit, description));
    }

    static String sha256Bounded(InputStream inputStream, long limit, String description,
            CumulativeBudget budget) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                total = addBounded(total, read, limit, description + " bytes");
                budget.consume(read);
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable for archive verification.", ex);
        }
    }

    static InputStream budgeted(InputStream inputStream, CumulativeBudget budget) {
        return new FilterInputStream(inputStream) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value >= 0) {
                    budget.consume(1);
                }
                return value;
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = super.read(bytes, offset, length);
                if (count > 0) {
                    budget.consume(count);
                }
                return count;
            }
        };
    }

    static long addBounded(long current, long increment, long limit, String description) throws IOException {
        if (current < 0 || increment < 0 || current > limit || increment > limit - current) {
            throw new IOException(description + " exceed limit " + limit + ".");
        }
        return current + increment;
    }

    static final class CumulativeBudget {

        private final long limit;

        private final String description;

        private long consumed;

        CumulativeBudget(long limit, String description) {
            if (limit <= 0) {
                throw new IllegalArgumentException("Cumulative byte budget must be positive.");
            }
            this.limit = limit;
            this.description = description;
        }

        void consume(long bytes) throws IOException {
            this.consumed = addBounded(this.consumed, bytes, this.limit, this.description);
        }

        long consumed() {
            return this.consumed;
        }
    }
}
