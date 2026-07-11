## Coco 生态

<table>
  <thead>
    <tr>
      <th width="24%">项目</th>
      <th width="46%">职责</th>
      <th width="30%">仓库</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Coco Framework</strong></td>
      <td>独立的 Spring Boot Web 服务器基础设施与稳定扩展边界。</td>
      <td><a href="https://github.com/patton174/coco-framework">coco-framework</a></td>
    </tr>
    <tr>
      <td><strong>Coco Admin</strong></td>
      <td>基于框架、使用普通业务代码实现的 ERP 产品与业务模块。</td>
      <td><a href="https://github.com/patton174/coco-admin">coco-admin</a></td>
    </tr>
    <tr>
      <td><strong>Coco Generate</strong></td>
      <td>开发期源码生成、可复用模板包和安全的生成文件管理。</td>
      <td><a href="https://github.com/patton174/coco-generate">coco-generate</a></td>
    </tr>
  </tbody>
</table>

依赖方向保持单向：Admin 运行时依赖 Framework，开发期可以使用 Generate；Generate 可以面向 Framework 契约产出代码；Framework 永远不依赖两个产品仓库。生成后的源码归业务应用所有，不会给业务运行时增加 Generate 依赖。
