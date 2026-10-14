# 文档索引 / Documentation Index

本页列出 Coco Framework 仓库里各类文档的位置，帮助你判断该读哪里、改哪里。

## 本目录

| 路径 | 内容 |
| --- | --- |
| [`wiki/`](wiki/README.md) | 公开 GitHub Wiki 的源目录；通过 `.github/scripts/wiki_sync.py` 镜像发布 |
| [`superpowers/`](superpowers/plans/2026-10-09-coco-framework-docs-ui-redesign.md) | 官网文档 UI 重设计的计划与规格（`plans/`、`specs/`） |

## 开发者 Wiki

[开发者 Wiki](https://github.com/patton174/coco-framework/wiki) 面向框架开发者、贡献者和 AI Agent，
涵盖架构、模块参考、构建与测试、约定和发布流程。

> Wiki 在维护者完成首次手动发布之前不可用，链接会先显示 404。在此之前请直接阅读
> [`docs/wiki/`](wiki/README.md) 下的 Markdown 源文件。

Wiki 页面只在 `docs/wiki/` 里修改，并通过 PR 评审；不要在 GitHub 网页上直接编辑 Wiki，下一次同步会覆盖这些修改。
校验命令：

```powershell
python .github/scripts/wiki_sync.py check
python .github/scripts/test_wiki_sync.py
```

## 相邻文档

| 路径 | 面向谁 | 内容 |
| --- | --- | --- |
| [`CHANGELOG.md`](../CHANGELOG.md) | 开发者、贡献者 | 各版本的重要变更，遵循 Keep a Changelog |
| [`website/docs/`](../website/docs/overview.md) | 业务使用者 | 官网文档：接入、Feature 开关、能力介绍；官网地址 [cocoframework.dev](https://cocoframework.dev/) |
| [`website/docs/releases/`](../website/docs/releases/index.md) | 业务使用者 | 版本说明与升级检查 |
| [`coco-support/coco-document/`](../coco-support/coco-document) | 框架维护者 | 架构、发布、审计、计划与规格文档 |
| [`AGENTS.md`](../AGENTS.md) | 贡献者、AI Agent | 项目定位、架构边界、开发规则与 Git 工作流 |
| [`CONTRIBUTING.md`](../CONTRIBUTING.md)、[`GOVERNANCE.md`](../GOVERNANCE.md) | 贡献者 | 贡献流程、受保护合并与发布控制 |
| [`SECURITY.md`](../SECURITY.md)、[`SUPPORT.md`](../SUPPORT.md) | 所有人 | 漏洞报告与获取帮助的渠道 |

## 生成文件

`README.md` 与 `README_CN.md` 由 `.github/readme/` 下的片段确定性生成，不要直接编辑。
修改片段后运行 `node .github/readme/scripts/render.mjs --write`，提交前运行 `node .github/readme/scripts/render.mjs --check`。
