## Install

Use `coco-parent` as the application parent and add the single starter dependency.

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>2.0.2</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

That is the whole setup. Unified responses, global exception handling, and TraceId propagation are on by default; business controllers stay ordinary Spring code.

Capabilities are selected declaratively, in YAML or with `@CocoFeatures`:

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
```

**→ [Getting started](https://cocoframwork.dev/en/getting-started)** walks through a first service end to end.
**→ [Feature toggles](https://cocoframwork.dev/en/feature-toggles)** lists every switch and its default.
