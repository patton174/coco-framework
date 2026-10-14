# Security Policy

[English](#english) | [简体中文](#简体中文)

## English

### Supported versions

The latest 2.x release is actively supported. Older release lines may receive a
fix at maintainer discretion, but users should upgrade to the latest published
version before reporting a problem.

### Reporting a vulnerability

Do not open a public Issue, Discussion, or pull request for an undisclosed
vulnerability. Use GitHub's
[private vulnerability reporting](https://github.com/patton174/coco-framework/security/advisories/new).

Include:

- affected Coco version and modules;
- impact and realistic attack prerequisites;
- a minimal reproduction or traceable code path;
- suggested mitigation when available;
- whether the report or exploit has been shared elsewhere.

Remove credentials, private keys, production data, and unrelated personal
information from the report.

The maintainer targets an initial acknowledgement within three business days
and a triage decision within seven business days. These are response targets,
not contractual service levels. Valid reports are coordinated privately until a
fix and release are available.

### Documentation and Wiki

Repository documents, the developer Wiki, and the changelog are public. Never put
credentials, private keys, tokens, production data, or personal information in
them; reference secrets by key name only. If you find sensitive information that
is already published in a document, a Wiki page, or its history, report it
through private vulnerability reporting instead of a public Issue, and do not
copy the value into your report. The Wiki source lives in `docs/wiki` and changes
only through reviewed pull requests.

## 简体中文

### 支持版本

当前最新的 2.x 版本处于主动维护状态。旧版本是否修复由维护者根据风险决定，报告
问题前应先升级到最新公开版本进行确认。

### 报告安全漏洞

未公开漏洞不得提交到公开 Issue、Discussion 或 PR。请使用 GitHub
[私密漏洞报告](https://github.com/patton174/coco-framework/security/advisories/new)。

报告应包含受影响版本和模块、实际影响与攻击前提、最小复现或可追踪代码路径、
可用的缓解方案，以及漏洞是否已向其他人披露。请移除凭据、私钥、生产数据和无关
个人信息。

维护者目标是在三个工作日内确认收到、七个工作日内完成初步分级；该时间是响应
目标而非服务承诺。有效漏洞会在修复和发布准备完成前保持私密协作。

### 文档与 Wiki

仓库文档、开发者 Wiki 和更新日志都是公开的，不得写入凭据、私钥、令牌、生产数据或个人信息；密钥只写键名。
如果发现文档、Wiki 页面或其历史中已经公开了敏感信息，请通过私密漏洞报告提交，不要创建公开 Issue，
也不要在报告中复制该值。Wiki 的源文件位于 `docs/wiki`，只通过评审后的 PR 修改。
