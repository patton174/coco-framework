# 官网部署

域名：`https://cocoframwork.dev`。

链路：Cloudflare → `coco-website` Tunnel → `127.0.0.1:18081` → Nginx 静态站。
源站地址由维护者的 SSH 配置管理，不写入公开部署文档。官网容器不绑定公网端口，既有服务保持独立。
Cloudflare DNS 使用隧道 CNAME，不配置指向源站的 A/AAAA 记录。

## 构建

在 `website` 目录执行 PowerShell：

```powershell
$env:COCO_SITE_URL='https://cocoframwork.dev'
$env:COCO_BASE_URL='/'
npm ci
npm run typecheck
npm run build
```

未设置环境变量时仍构建现有 GitHub Pages 地址，兼容原发布工作流。
字体通过 Fontsource 随站点打包，无 Google Fonts 运行时请求。

## 服务器布局

- `/opt/coco-website/www/releases/<release>`：每次发布的完整 build 内容。
- `/opt/coco-website/www/current`：指向当前 release 的相对软链接。
- `/opt/coco-website/config/nginx.conf`：本目录 Nginx 配置。
- `/opt/coco-website/config/tunnel.yml`：本目录隧道配置。
- `/opt/coco-website/tunnel/credentials.json`：仅此隧道的凭据，目录 700、文件 600；禁止提交。
- `/opt/coco-website/bin/cloudflared`：2026.8.3，Linux amd64。
- `coco-website`：Nginx 容器，自动重启，挂载整个 www 目录。
- `coco-website-tunnel.service`：systemd 服务，开机启动，失败自动重启。

账户级 `cert.pem` 仅保存在管理员本机，不上传服务器。
当前没有自动发布流水线；源码改动需经过仓库正常 PR 流程。

## 发布与回滚

上传新构建到一个新 release 目录，检查后通过临时链接原子切换：

```sh
cd /opt/coco-website/www
ln -s releases/<release> current.next
mv -Tf current.next current
curl -fI http://127.0.0.1:18081/
```

回滚时用同样方式指向上一个 release，保留旧目录直到确认发布成功。
回滚后的旧页面可能引用旧哈希资源，发布时应将上一版本 assets 合并保留到新版本 assets，避免打开中的页面或边缘缓存引用失效。

## 检查

```sh
docker exec coco-website nginx -t
systemctl status coco-website-tunnel
journalctl -u coco-website-tunnel -n 30 --no-pager
curl -fI http://127.0.0.1:18081/getting-started
curl -fI https://cocoframwork.dev/en/getting-started
```

HTML 每次验证更新；带哈希的 assets 使用一年不可变缓存；Nginx 启用 gzip。
首次上线已验证 CSS 从 Cloudflare MISS 变为 HIT。
2026-09-07 从国内服务器无代理请求首页返回 200，单次总耗时约 1.45 秒；
另一台国外服务器约 0.48 秒。该结果仅代表单点 HTTP 请求，不代表浏览器整页或所有运营商性能。
国内探测观察到 AMS 边缘路由，不能据此宣称已有大陆节点加速。
