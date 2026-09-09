# OneDrive / 云盘镜像设计

## 用户流程

1. 在设置页点击“选择云盘目录”。
2. 在 Android 系统文件选择器中选择 OneDrive 或其他支持 Storage Access Framework 的云盘目录。
3. LifeTrace 持久保存系统授予的目录 URI 权限，并立即镜像所有现有日记。
4. 后续每次保存都会更新云端副本；删除前先将完整副本写入 LifeTrace/Trash。

## 目录格式

```text
<用户选择的目录>/
└── LifeTrace/
    ├── Entries/
    │   └── <entry-id>/
    │       ├── entry.json
    │       └── photos/
    └── Trash/
        └── <deleted-at>-<entry-id>/
            ├── entry.json
            └── photos/
```

## 数据语义

- Room 数据库仍是手机端事实来源。
- 云盘镜像是可读、可迁移的灾备副本，不是多设备实时合并协议。
- 云端写入失败不会回滚已经成功的本地保存，设置页会显示失败状态。
- 启用镜像后，删除会先归档；归档失败时本地删除会被阻止。
- 分享链接通常不授予第三方应用稳定写权限，因此不接收或硬编码 OneDrive 分享链接。
- 真正的双向、多设备、端到端加密同步仍属于 M4。
