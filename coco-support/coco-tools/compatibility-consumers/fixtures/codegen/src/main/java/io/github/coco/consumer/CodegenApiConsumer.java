package io.github.coco.consumer;

import io.github.coco.feature.codegen.core.CocoCodeGenerator;
import io.github.coco.feature.codegen.crud.CocoCrudSpec;

public final class CodegenApiConsumer {

    private CodegenApiConsumer() {
    }

    public static String apiTypes() {
        return CocoCodeGenerator.class.getName() + "," + CocoCrudSpec.class.getName();
    }
}
