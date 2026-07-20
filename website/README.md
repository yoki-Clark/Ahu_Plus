# 安大+ 官网

`website/public/` 是可直接发布的静态站点，`website/deploy/` 是适配现有腾讯云 Lighthouse 的 Caddy + Docker Compose 配置。站点不需要数据库、Node.js 运行时或后台 API。

## 本地预览

在仓库根目录执行：

```powershell
python -m http.server 4173 --directory website/public
```

然后打开 `http://127.0.0.1:4173/`。

不能直接双击 `index.html` 预览，因为页面会请求 `/release.json`，并且生产环境使用根路径资源。

## 发布信息

下载区域读取 `public/release.json`。该文件由仓库根目录
`release/release-state.json` 生成，禁止手工修改。发布新版时由发布工具同步以下字段：

- `version` 与 `versionCode`
- `fileName` 与 `fileSize`
- `sha256`
- `downloadUrl`
- `publishedAt`

版本、文件大小和 SHA-256 与仓库根目录稳定版发布清单一致；`downloadUrl` 指向对应版本的 Gitee Release 资产。候选版本只会在 dry-run 输出目录生成预览，不会提前覆盖官网稳定版信息。

## Lighthouse 首次上线

现有基础设施记录表明服务器是上海 Lighthouse、Ubuntu 24.04 Docker 镜像，Docker 与 Compose 已完成验证，公网 `80/443` 尚未开放。域名与备案完成后按以下顺序上线：

1. 在 DNSPod 为 `ahuplus.online` 添加 `A` 记录，指向 Lighthouse 公网 IP。
2. 等待 DNS 生效，确认 `Resolve-DnsName ahuplus.online` 返回该公网 IP。
3. 在 Lighthouse 实例防火墙中向公网开放 TCP `80` 和 `443`；不要开放 `3000`、`8080`、数据库端口或 Docker API 端口。
4. 将 `website/` 上传到服务器的 `/opt/ahu-plus/website/`。该目录不包含任何凭据。
5. 在服务器创建部署环境文件：

   ```bash
   cd /opt/ahu-plus/website/deploy
   cp .env.example .env
   nano .env
   ```

   把 `ACME_EMAIL` 改为证书到期通知邮箱。`.env` 不提交 Git。
6. 校验并启动：

   ```bash
   docker compose config
   docker compose pull
   docker compose up -d
   docker compose ps
   docker compose logs --tail=100 web
   ```

7. 从本机验证：

   ```powershell
   curl.exe -I https://ahuplus.online/
   curl.exe https://ahuplus.online/release.json
   ```

## 下载托管与 CDN 边界

- 官网下载直接使用 Gitee Release 资产，不经过 `ahuplus.online`、Lighthouse 或腾讯云 CDN，因此官网链接被传播不会消耗 CDN 资源包。
- 发布新版时先完成本地 dry-run，再在 Gitee 创建正式 Release 并上传 APK；确认固定资产 URL 可用后，使用发布工具的 `promote --apply` 更新状态与生成清单。
- 腾讯云 CDN 预留给 App 内更新使用，当前网站部署配置不创建 CDN 加速域名，也不托管 APK 源站文件。
- App 客户端的检查冷却、显式下载、单任务和重试退避只能减少正常客户端的误用，不能阻止攻击者复制 CDN URL 后直接请求。
- 后续启用 CDN 更新时，仍需在 CDN 侧设置用量硬上限、异常告警、单 IP 限频和规范缓存键。需要更强防刷时，由后端生成短期签名 URL；URL 鉴权密钥不能放进 APK 或前端 JavaScript。

## 更新站点

把新的 `public/` 同步到原目录后，静态文件立即生效。配置未变化时无需重启 Caddy；如修改 `Caddyfile`，执行：

```bash
cd /opt/ahu-plus/website/deploy
docker compose exec -w /etc/caddy web caddy validate --config Caddyfile
docker compose exec -w /etc/caddy web caddy reload --config Caddyfile
```

## ICP 备案号

网站底部已经预留备案链接，但默认隐藏。取得准确备案号后，在 `public/index.html` 中找到 `data-icp`，填入备案号并移除 `hidden`。不要猜测或使用域名备案订单号代替正式 ICP 备案号。

公安联网备案如适用，应在网站开通后按属地要求办理，并在完成后添加对应链接。
