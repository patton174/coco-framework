## Tested end to end

Business and HTTP acceptance tests live in [`coco-admin/framework-acceptance`](https://github.com/patton174/coco-admin/tree/main/framework-acceptance):

- **Basic** (no database): responses, exceptions, i18n, trace, signature, encryption, replay protection.
- **Full** (H2 + MyBatis-Plus): security assertions, tenant SQL isolation, data-permission filtering, audit events.
