package io.github.coco.config;

final class CocoConfigFacadeFqcnCompileContract {

    private CocoConfigFacadeFqcnCompileContract() {
    }

    static Class<?> autoConfigurationType() {
        return CocoConfigAutoConfiguration.class;
    }

    static Class<?> propertiesType() {
        return CocoProperties.class;
    }
}
