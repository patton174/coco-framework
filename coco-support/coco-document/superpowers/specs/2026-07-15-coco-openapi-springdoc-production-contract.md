# Coco OpenAPI SpringDoc Production Contract

## Scope

`coco-openapi` remains independent of SpringDoc at compile and runtime. SpringDoc adaptation is activated only when a compatible `OpenApiCustomizer` SPI and its Swagger model classes are present.

## Compatibility and Overrides

- Missing, linkage-broken, or incompatible SpringDoc APIs must leave the Coco metadata provider available and must not register an adapter bean.
- `coco.openapi.enabled=false`, `coco.openapi.springdoc.enabled=false`, and the disabled `openapi` feature prevent the relevant Coco infrastructure from being registered.
- An application bean named `cocoSpringDocOpenApiCustomizer` replaces the default adapter. Other application `OpenApiCustomizer` beans compose with it.

## SpringDoc Components

When `coco.openapi.springdoc.response-schemas-enabled=true` (the default), the adapter registers only these bounded components:

- `CocoApiResponse`: `success`, `code`, `message`, and nullable `data`.
- `CocoApiErrorResponse`: the same fields, with `success` defaulting to `false`.

These components intentionally omit response trace IDs and paths, and never derive tenant identifiers, data-permission state, user identifiers, keys, nonces, tokens, or secrets from Coco runtime context. Applications remain responsible for documenting their own domain response payloads and security schemes.

## AOT

The optional adapter registers public reflective access to the Swagger model types it uses and a JDK proxy hint for `OpenApiCustomizer`, only when the compatible SpringDoc API is present at AOT build time.
