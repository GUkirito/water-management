# Tauri 自动更新打包发布指南

本文档说明村级自来水管理系统的 Windows 桌面安装包、自动更新文件和 WebView2 前置依赖的发布方式。

适用目录：

```text
D:\IdeaProjects\water-management
```

## 核心结论

1. `MicrosoftEdgeWebView2RuntimeInstallerX64.exe` 只需要在缺少 WebView2 Runtime 的电脑上安装一次。
2. 普通版本更新只上传主程序安装包、对应 `.sig` 和 `latest.json`。
3. `latest.json` 只指向主程序 NSIS 安装包，不指向 WebView2 安装器。
4. WebView2 离线安装器可以长期放在 GitHub Releases 中，作为首次部署前置依赖。

## 当前发布策略

项目采用“小主包 + 单独 WebView2 前置依赖”的方案。

| 文件 | 用途 | 是否每个版本都必须上传 |
| --- | --- | --- |
| `_<版本>_x64-setup.exe` | 主程序 NSIS 安装包，自动更新使用 | 是 |
| `_<版本>_x64-setup.exe.sig` | 主程序安装包签名 | 是 |
| `latest.json` | 自动更新索引文件 | 是 |
| `村级自来水管理系统_<版本>_x64_zh-CN.msi` | 手动安装用 MSI | 可选 |
| `村级自来水管理系统_<版本>_x64_zh-CN.msi.sig` | MSI 签名 | 可选 |
| `MicrosoftEdgeWebView2RuntimeInstallerX64.exe` | WebView2 离线前置安装器 | 只需上传一次，除非你要更新 WebView2 离线包 |

## WebView2 到底要不要每次安装

不需要。

WebView2 Runtime 是 Windows 桌面 WebView 的运行环境。安装一次后，后续系统升级不需要再安装它。只有下面情况才需要运行：

1. 新电脑第一次安装本系统，且缺少 WebView2 Runtime。
2. 电脑系统重装后 WebView2 Runtime 不存在。
3. 办公电脑不能联网，主安装包无法在线拉取 WebView2 bootstrapper。

首次部署推荐顺序：

```text
1. 运行 MicrosoftEdgeWebView2RuntimeInstallerX64.exe
2. 运行 _<版本>_x64-setup.exe
```

后续升级只需要运行或自动下载主程序安装包：

```text
_<版本>_x64-setup.exe
```

## build-tauri.bat 负责什么

`build-tauri.bat` 只负责当前版本主程序发布产物：

1. 构建前端。
2. 构建 Spring Boot 后端 JAR。
3. 如果 `target\custom-jre` 不存在，则创建自定义 JRE。
4. 构建 Tauri 的 MSI 和 NSIS 安装包。
5. 生成 `.sig` 签名文件。
6. 复制主安装包和 `.sig` 到 `installer` 目录。
7. 生成 `installer\latest.json`。

它不会每次重新生成 `MicrosoftEdgeWebView2RuntimeInstallerX64.exe`，也不会把 WebView2 离线安装器写进 `latest.json`。

## latest.json 哪个 URL 才是准的

以 `installer\latest.json` 中的这一项为准：

```json
{
  "platforms": {
    "windows-x86_64": {
      "url": "https://github.com/GUkirito/water-management/releases/latest/download/_<版本>_x64-setup.exe"
    }
  }
}
```

这个 URL 由 `build-tauri.bat` 的 `Generating latest.json...` 步骤自动生成。

规则：

1. URL 必须指向 GitHub Release 中真实上传的 NSIS `.exe` 文件。
2. URL 中的文件名必须和 `installer` 目录中的 `.exe` 文件名一致。
3. `signature` 必须来自同名 `.exe.sig` 文件。
4. `latest.json` 必须使用 UTF-8 无 BOM。

当前仓库地址固定为：

```text
https://github.com/GUkirito/water-management
```

因此自动更新下载地址固定形如：

```text
https://github.com/GUkirito/water-management/releases/latest/download/_<版本>_x64-setup.exe
```

WebView2 前置安装器即使也放在 Release 中，也不是自动更新 URL。

## 每次发布操作步骤

### 1. 修改版本号

同时修改：

```text
src-tauri\tauri.conf.json
src-tauri\Cargo.toml
```

示例：

```text
1.7.2 -> 1.7.3
```

版本号必须递增，否则旧版本客户端会认为没有更新。

### 2. 更新发布说明

打包前，先独立更新项目根目录的 `release-notes.txt`，内容应为本次版本需要展示给用户的更新说明。

`build-tauri.bat` 会读取该文件并写入 `installer\latest.json` 的 `notes` 字段；桌面端发现新版本后，会在用户确认是否下载的窗口中展示这段内容。

不要把中文发布说明重新内嵌到 `build-tauri.bat` 的 PowerShell 命令中。中文应只维护在 UTF-8 编码的 `release-notes.txt` 中，避免批处理和 PowerShell 的编码、转义差异破坏内容。

### 3. 运行打包脚本

```powershell
cd D:\IdeaProjects\water-management
.\build-tauri.bat
```

打包完成后，检查：

```text
installer\_<版本>_x64-setup.exe
installer\_<版本>_x64-setup.exe.sig
installer\latest.json
```

可选文件：

```text
installer\村级自来水管理系统_<版本>_x64_zh-CN.msi
installer\村级自来水管理系统_<版本>_x64_zh-CN.msi.sig
```

### 4. 核对 latest.json

重点核对：

1. `version` 是新版本号。
2. `url` 文件名和实际 `.exe` 文件名完全一致。
3. `signature` 和 `.exe.sig` 内容一致。
4. 文件没有 BOM。

可用 PowerShell 简单检查：

```powershell
$json = Get-Content installer\latest.json -Raw -Encoding UTF8 | ConvertFrom-Json
$json.version
$json.platforms.'windows-x86_64'.url
$json.platforms.'windows-x86_64'.signature.Length
```

### 5. 创建 Git 标签

示例版本为 `1.7.3`：

```powershell
git tag v1.7.3
git push origin v1.7.3
```

### 6. 上传 GitHub Release

打开：

```text
https://github.com/GUkirito/water-management/releases
```

创建新 Release，并上传：

```text
installer\_<版本>_x64-setup.exe
installer\_<版本>_x64-setup.exe.sig
installer\latest.json
```

可选上传：

```text
installer\村级自来水管理系统_<版本>_x64_zh-CN.msi
installer\村级自来水管理系统_<版本>_x64_zh-CN.msi.sig
```

首次部署或办公电脑离线部署时，Release 中还应保留：

```text
MicrosoftEdgeWebView2RuntimeInstallerX64.exe
```

这个文件不需要每个版本重新上传一次。

## 首次安装和后续更新的区别

### 普通联网电脑

直接运行：

```text
_<版本>_x64-setup.exe
```

如果电脑缺 WebView2，安装包会尝试在线安装 WebView2 bootstrapper。

### 办公电脑或离线电脑首次安装

先运行：

```text
MicrosoftEdgeWebView2RuntimeInstallerX64.exe
```

再运行：

```text
_<版本>_x64-setup.exe
```

### 已安装过 WebView2 的电脑

后续只需要主程序安装包。自动更新也只下载主程序安装包。

## 为什么主安装包现在变小了

当前 Tauri 配置使用：

```json
{
  "webviewInstallMode": {
    "type": "downloadBootstrapper",
    "silent": true
  }
}
```

这意味着主安装包不会内置约 194 MB 的 WebView2 离线安装器，所以 NSIS 主包约 120 MB 左右。

如果改成：

```json
{
  "webviewInstallMode": {
    "type": "offlineInstaller",
    "silent": true
  }
}
```

主安装包会重新变成约 320 MB 左右，但能在完全离线环境中自己安装 WebView2。

## 常见问题

### 每次打包都会生成 WebView2 安装器吗

不会。当前脚本不生成 WebView2 离线安装器。

WebView2 离线安装器来自微软官方文件，或者来自 Tauri 本地缓存。既然已经上传到 GitHub Releases，就把它作为固定前置依赖保留即可。

### WebView2 安装器要不要写进 latest.json

不要。

`latest.json` 是 Tauri 自动更新用的，只描述主程序更新包。WebView2 是系统运行时前置依赖，不属于应用版本更新包。

### 自动更新到底下载哪个文件

下载 `latest.json` 里 `platforms.windows-x86_64.url` 指向的文件，也就是：

```text
_<版本>_x64-setup.exe
```

### GitHub Release 里 latest.json 和安装包文件名必须一致吗

必须一致。

如果 Release 里上传的是：

```text
_1.7.3_x64-setup.exe
```

那么 `latest.json` 里的 URL 最后也必须是这个文件名。

### 办公电脑已经装过 WebView2，还要再装吗

不用。

只要已经安装过 WebView2 Runtime，后续直接安装或自动更新主程序即可。

### 首次启动检查更新失败后为什么没有反复提示

首次启动自动检查失败时，桌面端只会短暂提示一次，并进入 24 小时冷却。冷却期间不会访问 GitHub，也不会再次弹出失败窗口。

设置页的“检查更新”不受冷却限制，始终可以手动触发；如果手动检查失败，会展示详细的中文错误信息。

## 发布前检查清单

- [ ] `src-tauri\tauri.conf.json` 版本号已递增
- [ ] `src-tauri\Cargo.toml` 版本号已递增
- [ ] `release-notes.txt` 已更新为本次版本说明
- [ ] `.tauri\update-key.pem` 还在
- [ ] 已运行 `.\build-tauri.bat`
- [ ] `installer` 中有 `_<版本>_x64-setup.exe`
- [ ] `installer` 中有 `_<版本>_x64-setup.exe.sig`
- [ ] `installer` 中有 `latest.json`
- [ ] `latest.json` 无 BOM
- [ ] `latest.json` 的 `url` 文件名和上传的 `.exe` 完全一致
- [ ] GitHub Release 已上传 `.exe`、`.exe.sig`、`latest.json`
- [ ] GitHub Release 保留 `MicrosoftEdgeWebView2RuntimeInstallerX64.exe` 作为首次部署前置依赖
