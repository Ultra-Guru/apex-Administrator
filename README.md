# apex-changelog-publisher

**更新日志发布器** —— [Android-Guru-Agent](https://github.com/Ultra-Guru/Android-Guru-Agent)
的管理端安卓工具：手机上写更新日志，一键发送到开发仓库，自动触发发版流水线，
新版主 App 升级后首启弹窗展示该日志。

## 闭环全景

```
本 App 写日志 ──发送──▶ 开发仓库 .changelog/unreleased/<时间戳>-<slug>.md
                              │（push main 自动触发 release.yml）
                              ▼
                    自动构建新版本 APK（版本号自动追加构建序号）
                    changelog.json 三落点：Release 资产 / 发布仓库 main / APK 内置
                              │
                              ▼
                    主 App 检测到新版本 → 更新 → 首启强制弹窗展示日志（每版一次）
```

- 主仓库侧实现见 PR：`Ultra-Guru/Android-Guru-Agent#384`（feat/changelog-channel）
- 主 App 读取点：`https://raw.githubusercontent.com/Ultra-Guru/Android-Guru-Agent-Release/main/changelog.json`

## 页面

1. **主页**：目标仓库卡（预填）+ 已暂存片段列表（下一版内容）+ 标题/正文输入
   （以「- 」开头的列表项 = 主 App 弹窗要点）+ 发送按钮（结果卡：提交链接 /
   构建进度入口）+ 本机历史；
2. **设置**：目标仓库 / 分支（默认 `main`，push 即自动发版）/ GitHub Token
   （密码框、可测试连接、附 fine-grained 勾选指引）。

## Token 配置（重要）

推荐 **fine-grained token**（`github_pat_` 开头）：

| 配置项 | 勾选 |
| --- | --- |
| Repository access | Only select repositories → 只选 `Ultra-Guru/Android-Guru-Agent` |
| Contents | **Read and write**（提交片段必须） |
| Metadata | Read-only（系统自动） |
| 其它权限 | 一律不勾 |
| Expiration | 建议 90 天，到期轮换 |

classic token（`ghp_`）也可用：只勾 `repo` scope。

安全口径：Token 仅存本机应用私有目录（`allowBackup=false`，无云备份），
不写入日志、不进入任何提交内容；泄露随时可在 GitHub 撤销。

## 安装

推送 main 即自动构建（`.github/workflows/build.yml`）：Actions 完成后到
[Releases](../../releases) 下载 `app-release.apk` 安装（debug 签名，个人工具语义）。

## 技术栈

Kotlin 2.0.21 · AGP 8.7.3 · Jetpack Compose（BOM 2024.12.01）· Material 3 ·
OkHttp 4.12 · kotlinx.serialization —— 与主仓库同栈；单模块、零 DI、零第三方 UI 库。

## 本地构建

```bash
gradle wrapper --gradle-version 8.14   # 仓库不含 wrapper jar（CI 同款生成）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（debug 密钥签名，可直接安装）
```
