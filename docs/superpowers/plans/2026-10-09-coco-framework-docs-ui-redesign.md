# Coco Framework 文档前端视觉重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不迁移 Docusaurus 的前提下，把 Coco Framework 文档站改造成以快速接入和连续阅读为中心的技术文档界面。

**Architecture:** 保留现有 Docusaurus 路由、双语数据、SEO、Prism 代码块和自定义主题包装。用 `custom.css` 集中管理视觉 token，用首页 CSS module 重排首页层级，用 DocItem Content 包装器补齐复制状态可访问性和轻量动效；首页内容数据只做结构需要的最小调整。

**Tech Stack:** Docusaurus 3.10.2、React 19、TypeScript、CSS Modules、Prism React Renderer、Lucide React、GSAP、现有 Bricolage Grotesque/JetBrains Mono 字体。

**Spec:** `docs/superpowers/specs/2026-10-09-coco-framework-docs-ui-design.md`

## Global Constraints

- 本次只处理 `coco-framework/website` 的前端体验，保留 Docusaurus 3、现有中英文路由、文档内容、SEO 元数据和代码示例。
- 参考 Starlight 的阅读宽度、侧边栏层级和内容优先布局，参考 Fumadocs 的组件化表达，但不把底层框架迁移到 Starlight、Fumadocs、Rspress 或 Nextra。
- 页面视觉令牌集中定义在 `website/src/css/custom.css`，组件 CSS 只引用令牌，不重复写颜色和阴影值。
- 正文与大文本/UI 组件分别满足 4.5:1 和 3:1，对焦点指示也满足 3:1。
- 容器最多 8px 圆角，优先使用平面分区和细边框；不使用大面积渐变、发光或厚重阴影。
- 内容默认以最终状态渲染；只有在组件挂载后由 JavaScript 添加动画类时才允许执行进入动画，禁止用初始 `opacity: 0` 隐藏 SSR 内容。
- `prefers-reduced-motion: reduce` 下取消进入、浮起、横移和计数器效果，但保留复制成功/失败等必要状态反馈。
- 现有公开的 `#capabilities` 锚点必须保留；新增 `#architecture`、`#getting-started`、`#ecosystem`、`#community` 稳定锚点。
- 移动端页面本身不能横向滚动，代码块和表格只能在自身容器内横向滚动；正文阅读列最大宽度约 65-75ch。
- 不新增图片、动画或运行时依赖；不修改 Java 模块、文档正文业务含义、发布流程、SEO 插件和国际化路由。
- `website/src/data/landing.ts` 的 `zh` 与 `en` 字段结构必须保持同步，术语统一为 `Coco Framework`、`Starter`、`Features`、`Foundation`。
- 保留当前工作区已有的 `website/src/css/custom.css`、`website/src/theme/DocItem/Content/useDocMotion.ts`、`website/tsconfig.json` 和 `.claude/` 改动，不执行重置或覆盖。

## Review Focus

- **SSR/无 JS 内容可见性：** 首页和文档内容不得以初始透明度隐藏；由 Task 3 的页面检查验证首屏文本在脚本禁用条件下仍存在且可见。
- **长文本与双语布局：** 中文长标题、英文复合词和按钮文案不能撑破导航/CTA/能力项；由 Task 2 的类型检查和 Task 4 的双语视口检查覆盖。
- **移动端滚动边界：** 页面不能出现 body 横向滚动，代码/表格必须内部滚动；由 Task 4 在 `390x844` 检查 `document.documentElement.scrollWidth === clientWidth` 覆盖。
- **主题切换对比度：** 浅色/深色 token 映射必须维持正文、辅助文字、链接和焦点环的 WCAG AA 对比度；由 Task 1 的 token 计算和 Task 4 的浏览器检查覆盖。
- **复制反馈无障碍：** 复制按钮必须可键盘触发，成功/失败必须通过 `aria-live="polite"` 反馈；由 Task 3 的组件检查和 Task 4 的键盘检查覆盖。

---

### Task 1: 建立全局视觉令牌和文档页基础皮肤

**Files:**
- Modify: `website/src/css/custom.css`
- Modify: `website/src/theme/DocItem/Content/styles.module.css`

**Interfaces:**
- Consumes: 当前 Docusaurus CSS variables、Prism theme、已有 navbar/sidebar/TOC/code block selectors。
- Produces: `--coco-canvas`、`--coco-surface`、`--coco-surface-muted`、`--coco-text`、`--coco-text-muted`、`--coco-border`、`--coco-accent`、`--coco-code-bg`、`--coco-code-border`、`--coco-focus`、`--coco-radius-sm`、`--coco-radius-md`、`--coco-space-1` 至 `--coco-space-8`、`--coco-shadow-subtle`，供首页和文档页 CSS 复用。

- [ ] **Step 1: 读取当前 token 和主题覆盖范围**

确认 `custom.css` 中现有 warm palette、渐变标题、表格/代码块/TOC/导航规则及工作区已有移动端调整；不删除与主题切换和 navbar 移动端抽屉相关的兼容规则。

- [ ] **Step 2: 替换根级浅色/深色 token**

在 `:root` 和 `[data-theme='dark']` 内添加并接入设计规格中的中性表面、墨色正文、teal 强调色、代码背景、圆角、间距和轻阴影。将 Docusaurus 变量映射到这些 token，保留 `--ifm-color-primary` 作为兼容别名但不再使用 `--coco-cream`/`--coco-amber` 驱动文档主视觉。

- [ ] **Step 3: 收敛全局文档规则**

修改 `.markdown`、表格、代码块、admonition、blockquote、H1/H2/H3、sidebar active、TOC active、pagination 和 focus 规则：取消 H1 渐变、暖色渐变标题栏和厚重阴影；正文阅读列约束为 `65-75ch`；保留内部滚动和现有移动端 TOC/sidebar 行为。

- [ ] **Step 4: 运行 CSS 静态检查和类型检查**

Run from `website`:

```powershell
npm run typecheck
```

Expected: `tsc` exits 0；CSS token 修改不改变 TypeScript 编译结果。

- [ ] **Step 5: Commit the global skin**

```powershell
git add website/src/css/custom.css website/src/theme/DocItem/Content/styles.module.css
git commit -m "refactor(website): establish neutral docs design tokens"
```

### Task 2: 重排首页信息结构并保持双语/锚点兼容

**Files:**
- Modify: `website/src/pages/index.tsx`
- Modify: `website/src/data/landing.ts`
- Modify: `website/src/pages/index.module.css`

**Interfaces:**
- Consumes: `landing.zh/en`、现有 `/getting-started`、`/overview`、`/features/*` 路由、`#capabilities` 链接。
- Produces: 单一语义 H1、带稳定 `id` 的首页区块、首屏 CTA、能力导览、架构边界、接入路径和低重量生态/社区区。

- [ ] **Step 1: 约束 landing 双语字段结构**

把 `landing` 的中文对象作为结构参考，为英文对象保留同一组字段和数组项；只改动重排所需的最小文案字段。不要把 Framework 首页扩展成 Admin 业务示例。

- [ ] **Step 2: 缩减首页交互逻辑**

保留 SEO schema、release 链接、Starter 代码和基础 CTA；移除文档式的多段计数器和长区块 reveal 依赖。首屏内容默认可见，保留轻量首屏进入和必要的 hover/focus 反馈，并确保 reduced-motion 时没有非必要动画。

- [ ] **Step 3: 实现首页区块和稳定锚点**

按规格顺序渲染入口首屏、`id="capabilities"` 能力导览、`id="architecture"` 架构边界、`id="getting-started"` 接入路径、`id="ecosystem"` 生态和 `id="community"` 社区。把 `pains` 信息并入首屏/能力边界文案，不再以四张同质化主卡片展示；保留六个 `features` 的现有文档路径。

- [ ] **Step 4: 更新首页样式类但不改变路由**

使用 CSS module 令牌重做首屏、能力导览、架构层、步骤和社区区。移除重复编号、大片 tinted 背景、卡片浮起和长阴影；在桌面使用两栏/三栏信息网格，在 390px 使用单列，代码面板排在 CTA 之后。

- [ ] **Step 5: 运行首页类型和翻译检查**

Run from `website`:

```powershell
npm run typecheck
npm run check-translations
```

Expected: both commands exit 0；`landing.zh/en` 使用的字段完整，Docusaurus 生成的主题翻译没有中文残留或过期 key。

- [ ] **Step 6: Commit the homepage**

```powershell
git add website/src/pages/index.tsx website/src/data/landing.ts website/src/pages/index.module.css
git commit -m "refactor(website): prioritize docs entry points on homepage"
```

### Task 3: 补齐文档内容包装器的无障碍和动效降级

**Files:**
- Modify: `website/src/theme/DocItem/Content/index.tsx`
- Modify: `website/src/theme/DocItem/Content/styles.module.css`
- Modify: `website/src/theme/DocItem/Content/useDocMotion.ts`

**Interfaces:**
- Consumes: Docusaurus `useDoc()` metadata、当前 raw Markdown copy flow、GSAP `ScrollTrigger`、现有 `readingTrack`/`copyBar`。
- Produces: 可键盘触发的 Markdown copy 控件、`aria-live="polite"` 状态反馈、无初始隐藏内容的轻量 route fade 和 reduced-motion 降级。

- [ ] **Step 1: 为复制状态增加语义反馈**

在现有 copy button 附近增加视觉上可隐藏但屏幕阅读器可读的状态节点，使用 `aria-live="polite"`、`role="status"` 或等价单一语义；成功/失败状态文本与按钮文本同步，不能只用颜色或图标。

- [ ] **Step 2: 收敛复制按钮视觉**

继续使用原生 `<button>` 和现有图标逻辑，使用新 token 的边框、焦点环和状态色；保证 `:focus-visible` 明显，保持 copy success/error 的文本反馈和原有 fetch/clipboard 失败路径。

- [ ] **Step 3: 保留进度线并删除正文结构 reveal**

让 `useDocMotion` 只初始化阅读进度线和极轻 route opacity transition；不添加 heading/card/paragraph 的隐藏或逐项 reveal。对 `prefers-reduced-motion: reduce` 直接跳过动画但将进度线置为完成态。

- [ ] **Step 4: 运行文档主题类型检查和构建**

Run from `website`:

```powershell
npm run typecheck
npm run build
```

Expected: TypeScript 和 Docusaurus production build 均退出 0，既有 Markdown copy flow 和双语文档路由可编译。

- [ ] **Step 5: Commit the DocItem behavior**

```powershell
git add website/src/theme/DocItem/Content/index.tsx website/src/theme/DocItem/Content/styles.module.css website/src/theme/DocItem/Content/useDocMotion.ts
git commit -m "refactor(website): make docs feedback accessible and calm"
```

### Task 4: 桌面/移动/主题视觉验收

**Files:**
- Modify: only if visual verification finds a defect in the Task 1-3 files above.
- Verify: rendered homepage and representative docs pages in both locales.

**Interfaces:**
- Consumes: Tasks 1-3 committed output, local Docusaurus dev server.
- Produces: verified responsive layout, focus order, theme contrast, anchor behavior and no page-level horizontal overflow.

- [ ] **Step 1: Start the local website server**

Run from `website`:

```powershell
npm run start -- --host 127.0.0.1 --port 3000
```

Expected: Docusaurus serves the Chinese homepage at `http://127.0.0.1:3000/coco-framework/` or reports the selected available port if 3000 is occupied.

- [ ] **Step 2: Inspect required viewports and routes**

Check these routes at `1440x900`, `1024x768`, and `390x844`: `/coco-framework/`, `/coco-framework/getting-started`, `/coco-framework/features/web-runtime`, and their `/en/` equivalents. Confirm first viewport includes brand positioning, CTA and capability entry; confirm docs content column is `65-75ch` on desktop.

- [ ] **Step 3: Verify keyboard and semantics**

Use keyboard-only navigation to reach navbar links, sidebar links, TOC links, language toggle, GitHub, copy Markdown, code copy controls and pagination. Confirm visible focus rings, one H1 per page, no skipped heading level, and `aria-live="polite"` copy feedback.

- [ ] **Step 4: Verify scrolling and theme behavior**

Check light/dark modes and reduced-motion mode. At `390x844`, verify `document.documentElement.scrollWidth === document.documentElement.clientWidth`; verify long code and tables scroll inside their own containers. Follow `#capabilities`, `#architecture`, `#getting-started`, `#ecosystem`, and `#community` links.

- [ ] **Step 5: Run final repository checks**

Run from `website`:

```powershell
npm run typecheck
npm run check-translations
npm run build
```

From repository root, after source changes:

```powershell
codegraph sync .
git diff --check
git status -sb
```

Expected: all commands exit 0; only intended website files plus the already-existing user changes remain dirty; no build artifacts are added.

- [ ] **Step 6: Commit visual fixes, if any**

```powershell
git add website/src/css/custom.css website/src/pages/index.tsx website/src/pages/index.module.css website/src/theme/DocItem/Content/index.tsx website/src/theme/DocItem/Content/styles.module.css website/src/theme/DocItem/Content/useDocMotion.ts
git commit -m "fix(website): polish responsive docs UI"
```
