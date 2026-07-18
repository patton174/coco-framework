package io.github.coco.feature.web.body;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CocoCachedRequestBodyTest {

    private static final String EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @Test
    void cachedBodyUsesContentMetadataAndDefensiveCopies() {
        byte[] source = "hello".getBytes(StandardCharsets.UTF_8);

        CocoCachedRequestBody body = new CocoCachedRequestBody(source, null, -1L, true);

        assertTrue(body.cached());
        assertEquals(5L, body.length());
        assertEquals(HELLO_SHA256, body.sha256());
        source[0] = 'j';
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), body.content());

        byte[] firstRead = body.content();
        byte[] secondRead = body.content();
        assertNotSame(firstRead, secondRead);
        firstRead[0] = 'j';
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), body.content());
    }

    @Test
    void cachedNullContentIgnoresHugeSuppliedLength() {
        CocoCachedRequestBody body = new CocoCachedRequestBody(null, null, Long.MAX_VALUE, true);

        assertTrue(body.cached());
        assertEquals(0L, body.length());
        assertEquals(EMPTY_SHA256, body.sha256());
        assertArrayEquals(new byte[0], body.content());
    }

    @Test
    void uncachedBodyDiscardsSuppliedMetadata() {
        byte[] source = { 1, 2, 3 };

        CocoCachedRequestBody body = new CocoCachedRequestBody(source, "provided", Long.MAX_VALUE, false);

        assertFalse(body.cached());
        assertEquals(0L, body.length());
        assertNull(body.sha256());
        source[0] = 9;
        assertArrayEquals(new byte[] { 1, 2, 3 }, body.content());
    }
}
