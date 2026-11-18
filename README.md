# 轻聊 · LightChat

> 一个 **8 MB** 的 DeepSeek 手机客户端。
> 不注册、不登录、不收集任何东西 —— **API Key 只存在你自己的手机上**。

单人写的玩具，但每个细节都按"我自己天天用"的标准抠过：流式输出、思考过程、人设注入、角色扮演排版、联网搜索、token 计费，全都对齐官方 App 的形态。

---

## ✨ 它能做什么

| | 功能 | 说明 |
|---|---|---|
| 💬 | 基础对话 | 流式输出、Markdown、代码块、可折叠的思考过程 |
| 🎭 | **每对话独立人设** | 一句话说清要什么，AI 负责写成完整提示词；也可以上传现成的设定，**一个字都不改**直接用 |
| 🎬 | **角色扮演排版** | 人设模式把输出拆成 `台词 / 动作 / 心理`：台词是气泡，动作和心理是灰字 —— 不再是一坨段落 |
| 🌐 | 联网搜索 | DeepSeek 官方全家桶 API，由模型自己判断要不要搜，回答带引用来源 |
| 🧠 | 深度思考 | 开关在输入框上方点一下就切，开思考时联网自动变成「深度思考搜索」 |
| 📎 | 附件 | 选文件丢给模型读（纯文本，单文件 200 KB 内） |
| 📊 | 用量统计 | 输入 / 输出 token、**缓存命中率**、生成速度 tok/s |
| 🔌 | 第三方接口 | 主机 + 路径可配，OpenAI 兼容中转直接接，自动降级为标准字段 |
| 💾 | 本地存储 | 对话全在本地 SharedPreferences，导出 Markdown 带走 |

---

## 🧠 几个刻意的设计决定

这几个地方看起来"反直觉"，但都是踩过坑之后定的。

**1. 提示词只在保存时处理一次，之后每轮原样注入最前面**
DeepSeek 是按 **token 前缀**命中缓存的：前缀里任何一个字变了，那个位置之后全部重新计费（命中价约为未命中的 1/10）。所以人设一旦生成就冻结，每轮逐字放最前面，绝不"每轮压缩/润色一下"。

**2. 约束放在对话最末尾，而不是开头**
SillyTavern 那套 `post_history_instructions` 早就证明了：**模型对靠近生成位置的内容更敏感**。同一句"保持人设"，写在开头会被几十轮对话稀释，写在末尾就立得住。所以人设归人设，每轮还会在历史之后追加一条短回执。

**3. 提示词不给用户看**
生成完只显示「已启用 · 约 1.2k tokens」。看得见就容易忍不住改，一改前缀就变，缓存全废 —— 而且角色扮演里提前读到完整设定，自己就先出戏了。

**4. 超预算只裁历史，且按整条裁**
系统提示词永远完整注入；历史从最旧的一条开始整条丢弃，绝不截半条 —— 截半条最容易让模型"前言不搭后语"。

**5. 签名固定，只发版本化的包**
`build.ps1` 每次自动 `versionCode + 1`，产物名带版本和时间戳（`LightChat-v1.4-16-20261118-2058.apk`）。名字每次都不一样，微信/浏览器就不会给你加 `(1)`，也不会覆盖安装失败。

---

## 🔧 自己编译

需要 JDK 17+ 和 Android SDK 35。

```powershell
cd android\LightChat
$env:JAVA_HOME="<你的 JDK 路径>"
$env:ANDROID_HOME="<你的 Android SDK 路径>"
& ".\gradle.bat" :app:assembleDebug
```

或者用仓库里的脚本（会顺手自增版本号并整理产物）：

```powershell
cd android
.\build.ps1        # 产物落在 android\dist\
```

> 网络坑（国内）：`dl.google.com` 的 TLS 会被掐，`sdkmanager` 要带 `-Dhttps.protocols=TLSv1.3`；
> Gradle 发行包走 `mirrors.huaweicloud.com`，Maven 依赖走阿里云镜像。

**签名**：仓库里带了 `android\LightChat\debug.keystore`（debug 证书，密码 `android`）。
留着它是为了让每次构建签名一致 —— 换了签名就只能卸载重装，聊天记录全没。
自己用的话可以换成你自己的 keystore，改 `app/build.gradle.kts` 里的 `signingConfigs` 即可。

---

## 📁 目录结构

```
android/                 Android 客户端（Kotlin + Jetpack Compose）
  LightChat/app/         UI、会话模型、API 客户端、预算计算
  build.ps1              构建 + 版本自增 + 产物命名
  install.cmd            adb 覆盖安装
server.py + index.html   电脑端：Python 标准库起的本地网页聊天
```

---

## ⚠️ 已知限制

- 附件只读纯文本；PDF / Word / 图片暂不解析（官方 API 有文件解析接口，还没接）。
- 第三方中转下，深度思考与联网搜索是 DeepSeek 官方能力，会自动跳过。
- 没有多设备同步 —— 设计上就不做云端，数据只在你手机里。

---

## 📄 License

MIT。拿去改，随便用。

---

<sub>Made for personal use, shared in case it saves someone a weekend.</sub>
