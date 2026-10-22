# 页间 · Leaf Reader

原生 Android 电子书阅读器，支持边读边译。当前源码版本 **1.3.0**；最低 Android 8.0，文字型 PDF 导入需要 Android 15。

- 书架、阅读历史、EPUB/TXT/DOCX 导入和文字型 PDF 提取。
- 字体、背景、行距、翻页与自动翻页。
- DeepSeek API / 免费翻译通道，译文缓存与原文切换。
- 各版本能力以 [版本记录](CHANGELOG.md) 与 [使用说明](leaf-android/使用说明.md) 为准。

[构建与签名](docs/BUILDING.md) · [数据与联网](docs/PRIVACY.md) · [第三方许可](THIRD_PARTY_NOTICES.md)

## 构建

```bash
python3 android-toolchain/setup.py
bash leaf-android/build.sh
bash leaf-android/test.sh
```

产出为未签名 APK；配置仓库外的私钥后运行 `bash leaf-android/sign.sh`。不向仓库提交任何 API Key、签名私钥、小说或书架备份。

这仍是开发测试项目。历史标签来自已有源码快照，经过发布前打包整理。项目整体尚未指定开源许可证，第三方文件保留各自许可。
