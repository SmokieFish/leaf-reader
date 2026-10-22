# 发布前安全与完整性检查

检查日期：2026-10-19。范围：五个已交付的原生 Android 源码快照、对应 APK，以及在其上新增的 v1.3.1 修复。未检查此前独立的网页/Python 翻译工具，也未将其放入此仓库。

## 发现与处理

| 项目 | 发现 | 处理 |
|---|---|---|
| APK 签名私钥 | 私人开发源码包曾附带测试 keystore | 所有待公开提交及标签排除私钥，`.gitignore` 与历史扫描阻止再次误提交；签名从仓库外配置 |
| 备份完整性 | 先恢复书籍/进度，再解析后面的缓存记录，损坏的后续记录可能造成部分修改 | v1.3.1 在任何恢复写入前校验整份记录，并核对已有同编号书籍正文 |
| 构建残留 | 生成目录未清理，已删除的类可能继续打包 | 公开构建脚本先清理 classes/gen/dex，逐版本干净构建通过 |
| 工具链完整性 | 旧下载脚本校验 SDK SHA-1，但未固定 ECJ 校验值 | 三项下载均锁定 SHA-256；ECJ 与测试 JSON JAR 另对照 Maven 发布校验值 |
| 第三方许可 | 部分测试接口只引用 LICENSE 路径，JSON JAR 缺少附带许可 | 补入 XMLPULL Public Domain、JSON-java Public Domain、Apache 2.0 等原始许可材料 |
| API / 阅读数据 | 对源代码、文件类型、历史对象和 APK 内容检查 | 未发现真实 API Key、用户小说正文、阅读数据库或书架备份进入待发布文件 |

## 各版本核验

| 标签 | 主要变化 | 干净构建 | 合成测试 |
|---|---|---|---:|
| v1.0.0 | 初版原生阅读器、书架、导入、样式与翻译 | 通过 | 9 |
| v1.1.0 | EPUB 导入修复、点击/滑动翻页、中央菜单 | 通过 | 33 |
| v1.2.0 | 个人主页、Key 本机加密、阅读进度预译 | 通过 | 60 |
| v1.2.1 | 关闭翻译显示原文，缓存复用 | 通过 | 68 |
| v1.3.0 | 提前后两页、上下文与按书保存译名 | 通过 | 95 |
| v1.3.1 | 备份预校验和发布前安全整理 | 通过 | 109 |

v1.3.1 另用未进入仓库的真实 EPUB 做 1 项导入回归，共 110 项。Android XML 导入测试使用 AOSP KXmlParser；加密测试检查 AES-GCM 编解码；迁移检查执行实际新增表 SQL，但不等同于设备上的 SQLiteOpenHelper 测试。

六个 APK 均通过 ZIP CRC、包名/版本检查、APK v2/v3 签名验证；证书指纹一致。SHA-256 与大小记录在 `apk-sha256.json`，发布资产另附校验文件。历史 APK 保留已交付的原文件，并不宣称重新构建可得到字节完全相同的签名 APK。

五个历史标签的应用 Java 源码逐文件 SHA-256 与交付快照一致，记录在 `source-snapshots-sha256.json`；构建/签名脚本、许可、文档是公开前整理内容。提交是在整理当天重建的版本历史，不冒充原始开发时间。

## 数据流与权限复核

仅声明 INTERNET 权限。使用系统文件选择器，无 WebView、广告或分析 SDK。HTTPS 翻译目标为固定服务域名，默认系统证书验证，禁用自动重定向；未发现信任所有证书的代码。v1.2 起的 API Key 使用 Android Keystore 管理的 AES-GCM 密钥加密，配置窗口阻止屏幕抓取，书架导出字段不含 Key。

EPUB/DOCX 内容按文字解析，不执行书内脚本；v1.1 起明确关闭外部 DTD 处理。导入有压缩体积、解压量和条目数量上限。备份中书籍编号不允许路径穿越，v1.3.1 在写入前验证所有关联位置、片段、缓存和译名。

## 仍需明确的边界

- 没有 Android 实机/模拟器运行，也没有真实付费 API 端到端测试；不能保证设备兼容性、翻译速度或译法完全一致。
- 这是源码复核、文件检查和回归验证，不是渗透测试、恶意软件认证或完整 CVE 扫描。
- 恢复数据格式校验会先完成；恢复过程中磁盘写入失败或设备断电的跨文件事务恢复机制尚未实现。
- 旧开发签名私钥曾在私人源码包中交付；公开仓库不含它。正式生产发行仍需维护者管理专用签名策略。
- v1.0.0 的 EPUB 问题保留在历史标签中；不能因为核心测试通过就将该版本称为无缺陷。
- GitHub Actions 工作流已准备，实际远程运行结果应在推送后查看；本报告的通过结果来自本地构建与测试。

## 主要来源

- Android signing-key guidance: https://developer.android.com/studio/publish/app-signing
- AOSP parser source: https://android.googlesource.com/platform/libcore/+/9edf43dfcc35c761d97eb9156ac4254152ddbc55/
- XMLPULL license: https://www.xmlpull.org/v1/download/unpacked/LICENSE.txt
- JSON-java license: https://raw.githubusercontent.com/stleary/JSON-java/20240303/LICENSE
- Artifact origins and hashes: `android-toolchain/toolchain-lock.json` and `leaf-android/tests/lib/README.md`
