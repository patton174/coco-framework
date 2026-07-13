package io.github.coco.compatibility.web;

import io.github.coco.feature.web.CocoWebFeature;
import io.github.coco.feature.web.encryption.CocoRequestDecryptor;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.response.CocoApiResponse;

final class CocoFeatureWebCompatibilityCompileProbe {

    private CocoFeatureWebCompatibilityCompileProbe() {
    }

    static Class<?>[] publicTypes() {
        return new Class<?>[] {
                CocoWebFeature.class,
                CocoRequestDecryptor.class,
                CocoApiResponse.class,
                CocoReplayStore.class
        };
    }
}
