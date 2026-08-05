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

## Lighthouse 部署

生产站点运行在 `https://ahuplus.online`，部署环境为上海 Lighthouse、Ubuntu 24.04、Docker Compose 与 Caddy。站点目录是 `/opt/ahu-plus/website/`，公网只开放 SSH、HTTP 和 HTTPS 所需端口。首次部署或重建时按以下顺序操作：

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

把新的 `public/` 同步到原目录后，静态文件立即生效。配置未变化时无需重启 Caddy。管理端口已通过 `admin off` 关闭，因此修改 `Caddyfile` 后应先验证配置，再重启 Web 容器：

```bash
cd /opt/ahu-plus/website/deploy
docker compose exec -w /etc/caddy web caddy validate --config Caddyfile
docker compose restart web
docker compose ps
```

## 网站备案号

网站底部展示两类备案号，链接至对应官方查询系统：

- **ICP 备案**：`皖ICP备2026023445号-1`，链接 `https://beian.miit.gov.cn/`。
- **公安联网备案**：`皖公网安备34020302000359号`，前置备案图标 `/assets/beian.png`，链接 `https://beian.mps.gov.cn/#/query/webSearch?code=34020302000359`。

备案号或图标变更时，需同步更新 `public/index.html` 中 `.footer-filings` 内的链接文字与图标，图标文件位于 `public/assets/beian.png`。

## Market API deployment

The website Caddy container also terminates HTTPS for `api.ahuplus.online`. The market index service is deployed from `server/market_index/compose.yaml` and joins the external `ahu-plus-net` network. Caddy proxies only `/market/*` to `market-api:8000`; the API and worker do not publish host ports.

Before deployment, apply `server/market_index/migrations/001_market_readonly_index.sql` and `002_market_readonly_topic_archive.sql`, create the untracked server `.env` from `.env.example`, and verify:

```bash
docker compose -f server/market_index/compose.yaml config
docker compose -f server/market_index/compose.yaml up -d
curl -fsS https://api.ahuplus.online/health
curl -fsS 'https://api.ahuplus.online/market/readonly/feed?limit=20'
curl -fsS 'https://api.ahuplus.online/market/readonly/archive/<known-topic-id>'
```

The collector performs a resumable initial backfill, stores one latest gzip-compressed source-row snapshot per topic (including the source comment preview), then performs incremental synchronization. The archive endpoint is an explicit fallback for topics removed from the source. Never place `MARKET_SOURCE_TOKEN` or the cursor-signing secret in this repository, the Android APK, Caddy configuration, logs, or API responses.
