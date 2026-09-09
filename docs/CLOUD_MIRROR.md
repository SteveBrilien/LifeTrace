# 外部目录镜像与 OneDrive 直连边界

## 当前已实现：Android 外部目录镜像

设置页的“外部目录镜像”使用 Android Storage Access Framework（SAF）。用户选择的是一个由系统文件提供器暴露的目录：它可以来自本地存储，也可以在相应云盘 App 正确注册文件提供器后来自该云盘。

这项能力**不是**“输入一个 OneDrive 分享链接后直接上传”，因此 UI 不再将本地目录选择器称为 OneDrive 链接。

### 目录格式

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

- Room 仍是当前安装实例的事实来源。
- 保存后更新外部目录副本。
- 删除前先归档到外部目录 `LifeTrace/Trash`；归档失败时阻止本地删除。
- SAF 授权 URI 只保存在应用本机，不进入 Git。

## 当前已实现：卸载后仍存在的本地灾备

LifeTrace 0.3.2 新增固定的 `Documents/LifeTrace` 永久备份。获得 Android 文件访问权限后：

1. 当前全部日记会同步到 `Documents/LifeTrace/Entries`。
2. 每次保存都会更新对应 JSON 与原图。
3. 删除内容会进入 `Documents/LifeTrace/Trash`。
4. 应用卸载不会删除 Documents 下的该目录。
5. 重装后，用户重新授予文件访问权限时，若 Room 为空，LifeTrace 自动扫描并恢复备份。
6. 为兼容 0.3.1 的 SAF 灾备，还会检查 Documents/Download 下一层目录里的 `LifeTrace/Entries`。

Android 卸载应用时会撤销该应用的权限，所以“自动恢复”发生在重装后重新授予必要的文件访问权限之后，而不是绕过系统权限零交互读取。

## 尚未实现：OneDrive 分享链接直连

真正的 OneDrive 直连计划使用 Microsoft 身份授权与 Microsoft Graph：

- Microsoft OAuth 登录 / token 生命周期管理；
- 最小化的 `Files.ReadWrite` 权限；
- OneDrive 目录绑定与上传；
- token 安全存储、撤销和刷新；
- 网络失败、冲突、删除墓碑与恢复。

在这些授权链完成之前，LifeTrace 不会把本地 SAF 目录伪装成“OneDrive 链接同步”。真正的双向、多设备、端到端加密同步仍属于 M4。
