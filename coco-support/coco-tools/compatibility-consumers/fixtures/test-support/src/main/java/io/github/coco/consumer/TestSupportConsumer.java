package io.github.coco.consumer;

import io.github.coco.test.CocoTestSupport;

public final class TestSupportConsumer {

    private TestSupportConsumer() {
    }

    public static String typeName() {
        return CocoTestSupport.class.getName();
    }
}
