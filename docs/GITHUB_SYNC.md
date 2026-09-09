# GitHub 同步与 Deploy Key

LifeTrace 的 GitHub 远端为 `SteveBrilien/LifeTrace`。Orange Pi 使用项目专用 deploy key，不复用其他项目的 SSH 私钥。

## 安全边界

- 私钥保存在项目根目录 `.private/github/lifetrace_deploy_ed25519`，该目录由 `.gitignore` 排除。
- 公钥可添加到 GitHub 仓库 `Settings → Deploy keys`。
- 需要从 Orange Pi 推送时，Deploy key 必须启用 **Allow write access**。
- 不把私钥、签名文件、云盘令牌、MCP 缓存或本地环境配置提交到 Git。

## 网络路径

Orange Pi 到 GitHub 的 22 端口当前不可可靠使用，因此 `origin` 使用 GitHub 官方 SSH-over-HTTPS 入口：

```text
ssh://git@ssh.github.com:443/SteveBrilien/LifeTrace.git
```

`known_hosts` 使用 GitHub 官方公布的 Ed25519 主机密钥。变更主机密钥前必须重新核对 GitHub 官方指纹，不能用跳过校验的方式绕过 SSH 安全检查。

## 发布顺序

1. `lifetrace_lint` 通过。
2. 提交源码、文档和 CHANGELOG。
3. 在干净提交上执行 `lifetrace_debug`。
4. 注册并发布 APK Artifact。
5. Deploy key 可写时推送 `main` 到 GitHub。
