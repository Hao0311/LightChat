# 轻聊（Android 原生）

手机端的轻量聊天 App：Kotlin + Jetpack Compose，直连 DeepSeek API，自带 Key，一个 APK 装到哪台手机就用哪台的配置。

## 装

把 `android\LightChat-debug.apk` 传到手机，点开安装（需要允许「安装未知来源应用」）。

- **安卓手机**：直接装。
- **纯血鸿蒙**：先装卓易通，再在卓易通里装这个 APK。
  （卓易通是容器，通知/后台会受限，聊天本身没问题。）

装好打开 → 点右上「设置」→ 填 API Key（DeepSeek 开放平台的 `sk-…`）→ 保存 → 开始聊。

## 功能

- 流式对话、Markdown 渲染、代码块高亮、可选「深度思考」
- 多会话，本地保存（SharedPreferences，不上传任何东西）
- 单会话导出 Markdown（`⋯ → 导出 Markdown`），存到手机任意位置；
  拿到电脑上直接拖进 DSH，或者让 DSH `read` 这个文件就能接着干活
- 每台手机各填各的 Key，互不影响

## 改

- 代码：`LightChat\app\src\main\java\app\lightchat\`
  - `MainActivity.kt` 界面（列表 / 聊天 / 设置）
  - `DeepSeek.kt` 流式请求
  - `Markdown.kt` Markdown 解析 + 代码高亮
  - `ChatModels.kt` 数据与本地存储
- 重新打包：双击 `android\build.cmd`，产物复制到 `android\LightChat-debug.apk`

## 工具链（便携，不污染系统）

`android\_toolchain\`：

| 目录 | 内容 |
|---|---|
| `gradle\` | Gradle 8.9（用你 D:\Downloads 那份 zip 解出来的） |
| `sdk\` | Android SDK：platform-35 / build-tools 35.0.0 / platform-tools |
| `gradle-home\` | Gradle 依赖缓存 |
| `android-home\` | adb 与调试用的用户目录 |

JDK 直接用 DevEco 自带的 `D:\Program Files\Huawei\DevEco Studio\jbr`（21）。

依赖走阿里云 Maven 镜像（直连 `dl.google.com` 的 Java 请求会被掐，见 `install-sdk.py` 里加了 TLS1.3 才通）。

## 已知边界

- 聊天记录存在 App 私有目录，卸载即清空；要留档就用「导出 Markdown」。
- 没有图片/文件上传（手机端先不做），没有「交给 DSH」。
- 图标是临时画的，要换改装 `res/mipmap-anydpi-v26/ic_launcher.xml` 和 `drawable/ic_launcher_fg.xml`。

