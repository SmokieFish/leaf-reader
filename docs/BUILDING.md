# 构建与签名

需要 Linux x86_64、Java 17+、Python 3.9+、Bash。源码为原生 Java/Android Views，不使用网页或 WebView。

```bash
python3 android-toolchain/setup.py
bash leaf-android/build.sh
bash leaf-android/test.sh  # v1.1 起提供；无真实小说也可运行合成文件测试
```

下载按 `toolchain-lock.json` 的 SHA-256 核验。首次下载约 130 MB；工具链不提交到 Git。可设置 `LEAF_TOOLCHAIN_DIR` 指向已经安装的同结构工具链。构建输出 `leaf-android/build/aligned.apk`，尚未签名，不能直接安装。

仓库不含私钥或默认签名密码。自行在仓库外用 `keytool -genkeypair` 创建并备份 keystore；或者使用你已经持有的私有签名文件。签名脚本读取以下环境变量：

| 变量 | 用途 |
|---|---|
| `LEAF_KEYSTORE` | 仓库外的 keystore 路径 |
| `LEAF_KEY_ALIAS` | 该 keystore 中的别名 |
| `LEAF_KEYSTORE_PASSWORD` | 从私密环境注入，不要写进脚本或提交 |
| `LEAF_KEY_PASSWORD` | 可选，默认为 keystore 密码 |

```bash
bash leaf-android/sign.sh
```

输出位于 `dist/LeafReader-Android-版本号.apk`，脚本会验证签名。更新已有安装需要兼容签名；任意生成的新密钥不能覆盖以前的 APK。旧开发私钥曾通过私人源码包交付，不应把那个源码包直接上传仓库。此仓库所有历史提交均已排除它。正式公开发行应由维护者管理专用签名流程。

从旧源码快照重建版本历史时，应用 Java 源码保留原样；签名脚本、干净构建、依赖校验与许可材料经过发布前整理。这些提交是在整理时创建的，并非伪造的原始开发日期。
