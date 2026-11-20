# QQ音乐轻听 v1.0（Android APK）

基于 **QQ 音乐官网网页版（y.qq.com）公开接口**制作的轻量级 Android 播放器。
纯原生 Java 实现，无第三方 SDK、无后台服务、无广告。

## 安装

1. 把 `QQMusicLite-v1.0.apk` 传到手机（微信/QQ/数据线均可）。
2. 在手机文件管理器中点击安装。
3. 系统提示"禁止安装未知来源应用"时，选择"仍要安装 / 允许"。

要求：Android 5.0（API 21）及以上。

## 功能

| 功能 | 说明 |
|---|---|
| 搜索 | 输入歌曲名或歌手名，回车或点"搜索"，返回最多 30 条 |
| 排行榜 | 热歌榜、新歌榜、飙升榜、欧美榜，点标签切换 |
| 播放 | 点列表任意歌曲直接播放；底部条支持播放/暂停 |
| 进度 | 拖动进度条跳转，显示当前时间/总时长 |
| 歌词 | **长按**列表中的歌曲，弹出歌词窗口 |
| 封面 | 正在播放歌曲自动加载专辑封面 |

## 使用的官网接口

| 用途 | 接口 |
|---|---|
| 搜索 | `https://c.y.qq.com/soso/fcgi-bin/client_search_cp` |
| 排行榜 | `https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg` |
| 播放地址 | `https://u.y.qq.com/cgi-bin/musicu.fcg`（`vkey.GetVkeyServer`） |
| 歌词 | `https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg` |
| 封面 | `https://y.gtimg.cn/music/photo_new/T002R300x300M000{albumMid}.jpg` |

全部接口已于 2026-11-20 实际请求验证通过。

## 已知限制

- **VIP / 版权受限歌曲无法播放**：官网对这类歌曲不返回播放地址，App 会明确提示"版权/VIP 限制，无法播放"。这是平台正常权限控制，本软件不做任何绕过。
- 未实现登录、歌单同步、评论、下载等功能。
- 音质由官网接口决定，通常为标准品质（m4a）。
- 接口属于非官方公开接口，腾讯可能随时调整；如失效需更新 `Api.java` 中的地址或参数。

## 合规说明

本项目仅用于个人学习与技术研究，与腾讯/QQ 音乐无隶属或授权关系。
请尊重音乐版权，支持正版；不要用于商业用途或侵犯他人权益。

## 技术信息

- 包名：`com.ima.qqmusiclite`
- 版本：1.0（versionCode 1）
- minSdk 21 / targetSdk 33 / compileSdk 35
- 签名：**固定自签名证书**（仓库内的 `debug.keystore`），v1 + v2 + v3 校验通过
- 签名证书 SHA1：`65:9D:D6:C1:70:43:98:EC:15:52:71:0E:6F:D6:B2:E9:D9:BD:56:86`
- 签名证书 SHA-256：`38:B2:05:FE:27:C5:E8:69:B3:08:1B:1F:12:AF:54:1B:76:E8:1E:9C:FB:70:20:5D:5B:2A:B1:67:4D:20:2A:EF`
- 文件：`QQMusicLite-v1.0.apk`，45 KB
- APK SHA-256：`ea8710e00e5e3f2598a784869cc71407860dcd12c8eb780a91e4528c2853b7a2`

## 签名一致性（可覆盖安装）

`debug.keystore` 已随源码提交到仓库。无论在本地还是 GitHub Actions 重新构建，
`build.sh` 都会复用这把固定的钥匙，**签名证书永不变化**，因此新版 APK 可以直接覆盖安装，
不会出现"签名不一致，请先卸载旧版"的提示。

如需重新生成 APK：本地执行 `bash build.sh`，或在 GitHub 仓库点 `Actions → Build APK → Run workflow`。

源码目录：`/sandbox/workspace/qqmusic-app/`
- `AndroidManifest.xml`
- `src/com/ima/qqmusiclite/Api.java`（接口层）
- `src/com/ima/qqmusiclite/MainActivity.java`（界面与播放器）
- `src/com/ima/qqmusiclite/Song.java`（数据模型）
- `res/mipmap-*/ic_launcher.png`（图标）
- `debug.keystore`（重新打包用的自签名密钥，请勿外传）
