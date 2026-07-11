## Coco Ecosystem

<table>
  <thead>
    <tr>
      <th width="24%">Project</th>
      <th width="46%">Responsibility</th>
      <th width="30%">Repository</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Coco Framework</strong></td>
      <td>Independent Spring Boot Web server infrastructure and stable extension boundaries.</td>
      <td><a href="https://github.com/patton174/coco-framework">coco-framework</a></td>
    </tr>
    <tr>
      <td><strong>Coco Admin</strong></td>
      <td>ERP product and business modules built with normal application code on top of the framework.</td>
      <td><a href="https://github.com/patton174/coco-admin">coco-admin</a></td>
    </tr>
    <tr>
      <td><strong>Coco Generate</strong></td>
      <td>Development-time source generation, reusable template packs, and safe generated-file ownership.</td>
      <td><a href="https://github.com/patton174/coco-generate">coco-generate</a></td>
    </tr>
  </tbody>
</table>

The dependency direction is intentionally one-way: Admin depends on Framework at runtime and may use Generate during development; Generate may target Framework contracts; Framework never depends on either product repository. Generated source belongs to the consuming application and does not add a runtime dependency on Generate.
