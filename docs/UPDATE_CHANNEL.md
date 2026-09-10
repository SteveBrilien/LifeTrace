# LifeTrace 应用内更新通道

LifeTrace 0.3.3 起内置更新检查、APK 下载、SHA-256 校验和系统安装器调用。

## 客户端固定入口

客户端只访问：

```text
https://trace.wmy-cloud.cn/update.json
```

更新清单建议格式：

```json
{
  "versionCode": 7,
  "versionName": "0.3.4",
  "apkUrl": "https://trace.wmy-cloud.cn/releases/LifeTrace-0.3.4.apk",
  "sha256": "64位十六进制SHA-256",
  "notes": "本次更新说明"
}
```

客户端要求：

- `versionCode` 必须大于当前应用才提示更新。
- `apkUrl` 必须使用 HTTPS，且主机必须是 `trace.wmy-cloud.cn`。
- `sha256` 必须为 64 位十六进制字符串；下载结束后逐字节校验，不匹配时拒绝安装。
- `notes` 可选；兼容读取旧字段 `releaseNotes`。
- Android 8+ 首次应用内安装时，系统可能要求用户授予 LifeTrace“安装未知应用”权限；授权后回到设置页即可继续安装已下载 APK。

## 服务端发布流程

每个版本应按以下顺序发布：

1. 使用固定 LifeTrace 签名链构建并通过 lint。
2. 计算 APK SHA-256。
3. 将 APK 发布到 `https://trace.wmy-cloud.cn/releases/<filename>.apk`。
4. 最后原子更新 `update.json`，避免客户端先读到尚未上传完成的 APK。
5. 从公网重新下载 APK 并再次校验 SHA-256。

## 当前基础设施状态

2026-09-10 检查时，`trace.wmy-cloud.cn` 已解析到 Azure 主机，但 HTTPS 虚拟主机尚未正确建立；HTTP 当前命中既有的 `WeChat Assistant Apple Bridge` 后端，`/update.json` 尚未提供。因此 0.3.3 客户端更新能力已实现，但要正式启用更新通道，还需要在 Azure/Caddy 上为该域名单独配置 HTTPS 与静态 `update.json` / `releases/` 路由，不能复用现有 `/android/latest.json`（该路径属于另一个应用）。
