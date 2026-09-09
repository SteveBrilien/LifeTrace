# LifeTrace 本地持久化与恢复

## 两层存储

LifeTrace 0.3.2 使用两层本地存储：

1. **应用私有层**：Room 数据库 + `filesDir/media`，用于正常运行，速度快且不暴露给其他应用。
2. **永久灾备层**：`Documents/LifeTrace`，保存可读 JSON 与原图，设计目标是即使 LifeTrace 被卸载也继续保留。

永久灾备目录结构：

```text
Documents/LifeTrace/
├── Entries/<entry-id>/
│   ├── entry.json
│   └── photos/
└── Trash/<deleted-at>-<entry-id>/
    ├── entry.json
    └── photos/
```

## Android 11 权限

为了在卸载重装后仍能自动定位一个固定的公共 Documents 目录，Android 11 版需要用户显式授予“所有文件访问”能力。LifeTrace 不尝试绕过系统授权。

授权后会立即：

- 若 Room 有数据：同步完整灾备；
- 若 Room 为空：扫描持久目录并自动恢复；
- 后续每次保存增量更新对应日记；
- 删除前归档到 Trash。

## 0.3.1 → 0.3.2 一次性签名迁移

0.3.2 开始使用固定 LifeTrace 项目签名。历史调试 APK 如果由不同临时 debug key 签名，Android 会拒绝直接覆盖。建议：

1. 在仍能打开旧版时，用旧版“云盘/目录镜像”把全部内容镜像到 Documents 或 Download 下的一个持久目录。
2. 确认其中存在 `LifeTrace/Entries/<entry-id>/entry.json` 与照片。
3. 卸载旧签名版本。
4. 安装 0.3.2。
5. 设置 → 永久本地备份 → 启用文件访问。
6. LifeTrace 会同时扫描标准 `Documents/LifeTrace` 和 Documents/Download 下一层的旧 `LifeTrace/Entries`，并在本地数据库为空时恢复。
7. 恢复后点“立即同步”，把数据统一归档到标准 `Documents/LifeTrace`。

从固定签名链开始，后续发布都必须复用 `.private/signing/` 中的同一签名材料，禁止重新生成后覆盖原签名。
