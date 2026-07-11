# Coco Framework Module Layout

## Scope

This stage changes repository ownership paths only. Maven artifact IDs, Java package names, runtime behavior, and public APIs remain unchanged so the move can be reviewed and verified independently from the later 2.0 naming migration.

## Ownership Groups

```text
coco-build/
  coco-bom/
  coco-parent/
  coco-maven-plugin/
coco-foundation/
  coco-api/
    coco-api-core/
  coco-common/
    coco-common-context/
    coco-common-exception/
    coco-common-i18n/
    coco-common-logging/
coco-spring/
  coco-config/
  coco-spring-boot-autoconfigure/
  coco-spring-boot-starter/
coco-features/
  coco-feature-*/
coco-support/
  coco-test/
```

## Boundaries

- `coco-build` owns dependency management, the recommended parent POM, and build plugins.
- `coco-foundation` owns stable contracts and reusable infrastructure that should not depend on concrete features.
- `coco-spring` owns Spring Boot configuration, runtime feature activation, and the composition-only starter.
- `coco-features` owns independently selectable server capabilities.
- `coco-support` owns test and development support that is not part of normal application runtime behavior.

The following pull requests will flatten legacy aggregate directories and rename 1.x artifacts and Java packages in smaller, independently buildable steps. They must preserve these ownership directions and keep every complete review diff below the repository Agent Review limit.
