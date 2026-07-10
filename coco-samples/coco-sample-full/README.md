# Coco Sample Full

This sample proves the full Coco infrastructure path with one starter dependency and normal Spring business code.

It uses:

- H2 and MyBatis-Plus for persistence.
- Coco trusted-header security context plus `CocoSecurity.requireRole`.
- Coco tenant SQL isolation on `tenant_id`.
- Coco data-permission SQL filtering on `owner_id`.
- A business `CocoAuditRecorder` that replaces the framework's default structured logger, plus explicit business audit publication.

The sample request adapter maps `X-Coco-Tenant-Id` and the authenticated principal into tenant and data-permission contexts. It intentionally remains application code because tenant and organization semantics belong to the business system.

> The trusted-header adapter is enabled only for demonstration. Production applications must accept these headers only from a trusted gateway or authentication filter.

## Verify

Install the framework artifacts, build the sample, then run the black-box flow:

```powershell
mvn -B install
mvn -B -f coco-samples/coco-sample-full/pom.xml verify
python coco-samples/coco-sample-full/scripts/verify_business_flow.py
```

The black-box flow proves that one request sees only the row matching both its tenant and principal, verifies role denial and missing tenant handling, and reads the resulting audit events.

## CRUD Source Generation

The sample includes `coco-codegen.example.yml` as an opt-in generator specification. Generate the editable source set under the ignored build directory:

```powershell
cd coco-samples/coco-sample-full
mvn coco:generate `
    "-Dcoco.codegen.spec=coco-codegen.example.yml" `
    "-Dcoco.codegen.outputDirectory=target/generated-codegen"
```

The goal is not bound to the build lifecycle and does not modify `src/main/java` in this example.

## Request Headers

| Header | Purpose |
| --- | --- |
| `X-Coco-Principal-Id` | Trusted upstream principal identifier. |
| `X-Coco-Roles` | Comma-separated roles such as `ORDER_READER`. |
| `X-Coco-Tenant-Id` | Business tenant selected by the sample adapter. |
| `X-Trace-Id` | Trace identifier propagated into the audit event. |
