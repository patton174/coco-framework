import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docsSidebar: [
    'overview',
    'getting-started',
    'feature-toggles',
    {
      type: 'category',
      label: '核心 Web',
      items: [
        'features/web-runtime',
        'features/request-security',
        'features/security-context',
      ],
    },
    {
      type: 'category',
      label: '数据访问',
      items: [
        'features/mybatis-plus',
        'features/pagination',
      ],
    },
    {
      type: 'category',
      label: '多租户与权限',
      items: [
        'features/tenant',
        'features/data-permission',
      ],
    },
    {
      type: 'category',
      label: '流控与可靠性',
      items: [
        'features/rate-limit',
        'features/idempotency',
        'features/lock',
        'features/scheduling',
      ],
    },
    {
      type: 'category',
      label: '平台能力',
      items: [
        'features/storage',
        'features/messaging',
        'features/cache',
        'features/notification',
        'features/audit',
        'features/openapi',
        'features/codegen',
        'features/infra',
      ],
    },
  ],
  skillsSidebar: [
    'skills/overview',
    'skills/install',
    'skills/usage',
  ],
};

export default sidebars;
