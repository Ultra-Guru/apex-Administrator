# apex-Administrator

**更新日志发布器** —— [Android-Guru-Agent](https://github.com/Ultra-Guru/Android-Guru-Agent)
的管理端安卓工具。手机上写更新日志，一键发送到开发仓库，自动触发发版流水线；
新版主 App 升级后首启弹窗展示该日志。

```
本 App 写日志（+ 配图）──发送──▶ 开发仓库单个原子 commit：
                              ├─ .changelog/unreleased/<时间戳>-<slug>.md（正文）
                              └─ .changelog/images/<时间戳>-<N>.jpg（配图，自动压缩）
                              │（push main 自动触发 release.yml）
                              ▼
                    自动构建新版本 APK（changelog.json 三落点，含 images 配图字段）
                    主 App 检测到新版本 → 更新 → 首启弹窗展示日志 + 图廊（每版一次）
```

- 主仓库侧实现：`Ultra-Guru/Android-Guru-Agent`（PR #384，已合并）
- 主 App 读取点：`https://raw.githubusercontent.com/Ultra-Guru/Android-Guru-Agent-Release/main/changelog.json`

## v2 —— 全局液态玻璃 + 单仓库补丁更新

### 液态玻璃（全局）

- 底材：**Kyant0 Backdrop**（`io.github.kyant0:backdrop:2.0.1`，Compose 最先进的液态玻璃库）
  —— 折射（lens）+ 模糊（blur）+ 色彩提升（vibrancy）+ 高光描边 + 内外阴影；
- 极光壁纸（薄荷 × 薰衣草 × 暖金，26s 漂移）为所有玻璃面提供折射底材，亮暗双态；
- 玻璃组件层：`GlassCard / GlassButton / GlassIconButton / GlassTopBar / GlassTextField / GlassBanner`；
- API 分级降级（库内置）：`<31` 无模糊、`<33` 无折射 → 纯半透明面，无需业务分支。

### 单仓库补丁更新（本 App 自身的热更新通道）

与主仓库双仓库体系同源的机制，收敛为**单仓库**：

```
PR 合并 / 直推 main ──▶ build.yml（串行）
    ① 版本自增：versionCode = max(main version.json, 最新 Release 资产, 地板)+1 防撞号
    ② 签名出包（keystore 走 Secrets）
    ③ xdelta3 增量补丁（上一版 APK → 本版 APK，-S lzma）
    ④ version.json（版本 / 下载 / SHA-256 / 补丁 / 证书指纹 / notes）
       ── Release 资产 + 提交 main 双落点（提交带 [skip ci] 防自触发）
    ▼
App「设置 → 软件更新」读取 main 的 version.json
    ▼
本机 == 补丁基底 → 下载差量 → 设备上 VCDIFF 合成（无命令行 / 无 root）→ SHA-256 对账 → 安装
本机较旧      → 自动回退全量 APK
```

- 客户端解码器：内置纯 Kotlin `VcdiffDecoder`（移植自主仓库，xdelta3 LZMA 二级压缩）；
- 双重校验：全链 SHA-256 + Android 安装器签名连续性检查；
- 启动静默检查，网络失败静默降级不打扰。

## v2.3 —— 配图通道（发送图片）

更新日志不再只是文字：配图截图直接随片段发布，主 App 弹窗图廊展示。

- **选图即落盘**：photo picker 多选（≤9 张），选中即复制进应用缓存目录 ——
  授权不跨进程也稳；缩略图即时预览、随时移除；
- **上传自律**：发送时压缩为最长边 ≤1600px / JPEG 85（单图典型 <300 KB），
  统一重编码 `.jpg`（杜绝 SVG / 动图等渲染面外溢）；
- **原子提交**（Git Data API：blobs → tree → commit → ref）：配图 `.jpg` 与
  片段 `.md` 进**同一个 commit** —— 只触发一次发版流水线，绝不出现
  「图传了一半」的中间态；并发撞车（恰逢 PR 合并）自动整体重试一次；
- **链路闭环**：片段尾追加 `![配图N](raw URL)` 引用 → 聚合端（主仓库
  `scripts/changelog.py`）提取进 `changelog.json` 的 `images` 字段 →
  主 App（v1.4.7+）弹窗要点下方图廊渲染，点开全屏查看（双击缩放 / 拖拽平移）。

## v2.3.1 —— 效果预览 + 一级置顶双保险

### 效果预览（发送前所见即用户所得）

发送前点「效果预览」，1:1 模拟主 App 升级首启的两步弹窗：

- **第 1 步「更新日志」**：本条手动日志的要点（与聚合端同规则的列表项剥壳）
  + 配图缩略图墙 —— 正是用户升级后第一眼看到的内容；
- **「下一步 · 功能变更」** → 第 2 步：本版合并的 PR 功能变更分组占位说明；
- 正文没有「- 」列表项时给出提示（弹窗将取首行作摘要）。

### 一级置顶双保险（主仓库 changelog 通道 v3.1）

「发布了却不显示更新内容」的根因是片段降级：一级判定此前只认
`<!-- source: publisher -->` 标记。v3.1 起为双通道：

1. **显式标记**：`source: publisher`（本 App v2.1+ 发送时自动携带）；
2. **git 溯源兜底**：片段由 commit message 含「via 发布器」的提交新增 ——
   老版本 App 发送的无标记片段也恒进一级置顶。

显式 `pr:` 标记恒二级（PR 功能变更），不再泄漏到组标题。跨多版本升级
（含增量补丁链）时，主 App 聚合区间内**全部版本**的日志 —— 要点、配图、
功能变更分组跨版本并集去重，一条不丢。

## 页面

1. **主页**：更新横幅（有新版本时）→ 目标仓库卡 → 已暂存片段（下一版内容）→
   标题 / 正文玻璃输入框 → 配图选择（相册多选 ≤9 张，缩略图可移除）→
   效果预览 + 发送（结果卡：提交链接 / 构建进度入口）→ 本机历史；
2. **设置**：软件更新卡（增量优先）→ 连接配置（目标仓库 / 分支 / Token）→
   fine-grained 勾选指引 → 快捷入口。

## Token 配置

推荐 **fine-grained token**（`github_pat_` 开头）：

| 配置项 | 勾选 |
| --- | --- |
| Repository access | Only select repositories → 只选 `Ultra-Guru/Android-Guru-Agent` |
| Contents | **Read and write**（提交片段必须） |
| Metadata | Read-only（系统自动） |
| 其它权限 | 一律不勾 |

classic token（`ghp_`）也可用：只勾 `repo` scope。

安全口径：Token 仅存本机应用私有目录（`allowBackup=false`，无云备份），
不写入日志、不进入任何提交内容；泄露随时可在 GitHub 撤销。

## 签名（meng411722）

- 密钥文件 `keystore.jks` 不入库：CI 经 `KEYSTORE_BASE64` Secret 解码到仓库根
  （`build.gradle.kts` 优先读取）；密码 / 别名经 Secret 环境变量注入，本地兜底字面量
  仅为无密钥时的占位（密码无密钥文件不可利用）；
- 无密钥时回退 debug 签名 —— PR 校验构建 / 本地开发不断链；
- v2.0.0 起更换为正式密钥：从 v1.0.0（debug 签名）升级需先卸载旧版（一次性），
  之后所有版本均可覆盖安装 / 增量更新。

## CI / CD

| 工作流 | 触发 | 行为 |
| --- | --- | --- |
| `ci.yml` | PR / 非主干 push | 编译 + Lint + 版本地板守护 |
| `build.yml` | PR 合并 / 直推 main | 版本自增 → 签名出包 → xdelta3 补丁 → version.json 双落点 → Release |

安装：Actions 完成后到 [Releases](https://github.com/Ultra-Guru/apex-Administrator/releases)
下载 `ApexAdministrator-*.apk`，或在已安装的发布器里「设置 → 软件更新」直接增量更新。

## 技术栈

Kotlin 2.4.21 · AGP 9.1.0（R8 9.4.24 固定）· Gradle 9.3.1 · Compose BOM 2026.09.00
（compileSdk 37）· Material 3 · Kyant0 Backdrop 2.0.1（液态玻璃）· OkHttp 4.12 ·
kotlinx-serialization —— 单模块、零 DI。

## 本地构建

```bash
gradle wrapper --gradle-version 9.3.1   # 仓库不含 wrapper jar（CI 同款生成）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
# （仓库根放 keystore.jks 时走正式签名，否则 debug 签名）
```
