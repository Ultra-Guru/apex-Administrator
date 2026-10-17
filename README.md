# apex-Administrator

**更新日志发布器** —— [Android-Guru-Agent](https://github.com/Ultra-Guru/Android-Guru-Agent)
的管理端安卓工具。手机上写更新日志，一键发送到开发仓库，自动触发发版流水线；
新版主 App 升级后首启弹窗展示该日志。

```
本 App 写日志 ──发送──▶ 开发仓库 .changelog/unreleased/<时间戳>-<slug>.md
                              │（push main 自动触发 release.yml）
                              ▼
                    自动构建新版本 APK（changelog.json 三落点）
                    主 App 检测到新版本 → 更新 → 首启弹窗展示日志（每版一次）
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

## 页面

1. **主页**：更新横幅（有新版本时）→ 目标仓库卡 → 已暂存片段（下一版内容）→
   标题 / 正文玻璃输入框 → 发送（结果卡：提交链接 / 构建进度入口）→ 本机历史；
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
下载 `Publisher-*.apk`，或在已安装的发布器里「设置 → 软件更新」直接增量更新。
