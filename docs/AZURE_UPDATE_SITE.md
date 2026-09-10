# Azure 更新站部署说明

LifeTrace 0.3.3 起，客户端固定从 `https://trace.wmy-cloud.cn/update.json` 检查更新，并只接受同一域名下的 HTTPS APK 地址。

## 为什么采用 Caddy + 私有 bridge backend

Azure 主机已经有一个 Docker Compose 管理的 Caddy 作为公网 TLS 入口。LifeTrace 更新站沿用已验证过的同类架构：

```text
Android LifeTrace
    -> HTTPS trace.wmy-cloud.cn
    -> Docker Caddy（TLS / 域名路由）
    -> Docker bridge gateway:18779
    -> lifetrace-update-site.service（用户级静态文件服务）
```

后端只监听 Caddy 容器所在 Docker 网络的 bridge gateway，不监听 `0.0.0.0`，因此公网只能经过 Caddy/TLS 访问。

另一个已经验证过的 Azure/Caddy 部署曾出现一个关键问题：宿主机的 Caddyfile 是 Docker 的“单文件 bind mount”。如果宿主侧使用原子替换更新 Caddyfile，运行中的容器仍可能继续引用旧 inode；单纯 `caddy reload` 并不足以让新配置生效。因此 LifeTrace cutover 脚本会先在当前 Caddy 镜像里验证新配置，再只 `--force-recreate` Caddy Compose service 重新建立 bind mount，同时保留时间戳备份和自动回滚。

## 文件

- `infra/azure/AZURE-LIFETRACE-UPDATE-CUTOVER.sh`
  - 自动发现正在运行的 Caddy container / Compose project / Caddyfile bind mount。
  - 自动发现 Caddy 所在 Docker 网络 gateway。
  - 安装 `lifetrace-update-site.service` 用户级静态后端。
  - 生成并验证独立的 `trace.wmy-cloud.cn` Caddy site block。
  - 只重建 Caddy service，不重启其他应用。
  - 等待 ACME/HTTPS 生效并进行公网健康检查；失败时恢复 Caddyfile 备份。
  - 可选：首次 cutover 时同时从一个临时 APK URL 拉取、校验 SHA-256 并发布当前版本。

- `infra/azure/AZURE-LIFETRACE-PUBLISH.sh`
  - 后续版本发布脚本。
  - 先原子写入 APK，再最后更新 `update.json`。
  - 发布后从 `trace.wmy-cloud.cn` 公网重新下载 APK，并再次验证 SHA-256。

## 首次部署

在 Azure 主机上取得 cutover 脚本后执行：

```bash
chmod +x AZURE-LIFETRACE-UPDATE-CUTOVER.sh

SOURCE_APK_URL='https://<temporary-source>/LifeTrace-0.3.3.apk' \
SOURCE_APK_SHA256='<64-char-sha256>' \
RELEASE_VERSION='0.3.3' \
RELEASE_VERSION_CODE='6' \
RELEASE_NOTES='LifeTrace 0.3.3' \
bash ./AZURE-LIFETRACE-UPDATE-CUTOVER.sh
```

成功结束必须看到：

```text
PRIVATE_UPDATE_BACKEND=PASS
CADDY_VALIDATE=PASS
AZURE_LIFETRACE_UPDATE_CUTOVER=PASS
```

公网验收：

```bash
curl -fsS https://trace.wmy-cloud.cn/healthz
curl -fsS https://trace.wmy-cloud.cn/update.json | python3 -m json.tool
```

## 后续发布

把已经固定签名、通过 lint 的 APK 放到 Azure 后：

```bash
bash ./AZURE-LIFETRACE-PUBLISH.sh \
  /path/to/LifeTrace-0.3.4.apk \
  7 \
  0.3.4 \
  '更新说明'
```

脚本会输出最终版本、SHA-256、manifest URL 和 APK URL，并对公网回下载文件进行完整哈希验证。

## 回滚原则

首次 cutover 脚本会在修改 Caddyfile 前生成：

```text
<Caddyfile>.pre-lifetrace-update-<UTC timestamp>.bak
```

若 Caddy 验证、容器重建或公网 HTTPS 验收失败，脚本会自动恢复该备份并再次只重建 Caddy service。`lifetrace-update-site.service` 是独立用户服务，即使更新站需要临时停用，也不会影响现有 WeChat、MCP 或其他域名的应用服务。
