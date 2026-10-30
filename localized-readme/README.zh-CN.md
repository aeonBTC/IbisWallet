# Ibis Wallet

Ibis 是一款自托管的模块化比特币钱包，注重直观设计、隐私和可定制性。

专为经验丰富的用户设计——没有手把手指导，没有辅助轮。

<img width="1000" height="417" alt="5" src="https://github.com/user-attachments/assets/45dcfdaa-71c4-4698-a284-5eef054f1e25" />

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="从 GitHub 获取 APK" height="80">](https://github.com/aeonBTC/ibiswallet/releases)

**语言:** [English](../README.md) · [Español](README.es.md) · [Русский](README.ru.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [日本語](README.ja.md) · 简体中文

## 主要功能

### Layer 1 — 比特币
- **多钱包** - 创建、导入、导出多个钱包并在它们之间切换
- **多助记词** - 导入钱包时支持 BIP39 或 Electrum 助记词
- **骰子熵** - 通过掷骰子生成助记词
- **多签钱包** - 导入多签描述符，协调 PSBT 签名，并作为共同签名人本地签名
- **观察钱包** - 导入 xpub/zpub、输出描述符或单个地址
- **导入私钥** - 清扫或导入私钥（WIF 格式）
- **静默支付** - 完整支持 BIP-352：发送和接收，默认使用 Frigate 服务器
- **硬件钱包签名** - 使用动态二维码或 .psbt 文件进行气隙签名
- **NFC PSBT 签名** - 通过 NFC 轻触将 PSBT 发送到硬件钱包，并以同样方式收回签名
- **离线签名器** - 在离线手机上将 Ibis 用作观察钱包 PSBT 的冷存储签名工具。
- **粉尘防护** - 标记可疑粉尘并将其排除在自动选币之外
- **选币控制** - 选择特定 UTXO、冻结/解冻、从单个输出发送
- **强制选币** - 可强制在链上发送前选择 UTXO 的选项
- **RBF 与 CPFP** - 提高未确认交易的手续费，包括支持多签的安全 PSBT 加费流程
- **默认启用 RBF** - 链上发送默认标记 replace-by-fee；可关闭
- **取消交易** - 通过 RBF 取消未确认的对外交易
- **手动广播** - 将任意已签名的原始交易直接广播到比特币网络
- **批量发送** - 在单笔交易中向多个收款方发送
- **消息签名** - 使用 BIP137 签名和验证消息
- **校验词助手** - 根据 11/23 词前缀计算缺失的校验词
- **BIP329 标签** - 符合行业标准的交易和地址钱包标签
- **交易搜索** - 按日期、地址或标签搜索交易历史
- **基于** [BDK](https://bitcoindevkit.org/)

### Layer 2 — 原生 Lightning、Ark、Spark 和 Liquid
- **模块化集成** - 为每个钱包选择启用的 Layer 2
- **Lightning** - 通过 LND（LND REST）、CLN（clnrest）或 NWC（NIP-47）连接远程节点
- **Ark** - 支持 VTXO 管理、Lightning 发送和接收、单笔交易上链、单边退出的 Ark L2（基于 [Bark](https://github.com/ark-bitcoin/bark)）
- **Spark** - 支持 Lightning 和链上发送接收、单边退出的 Spark L2（基于 [Breez-SDK](https://github.com/breez/spark-sdk)）
- **Liquid** - 支持保密交易和 USDt 的完整 Liquid 钱包（基于 [LWK](https://github.com/Blockstream/lwk)）
- **Liquid 观察钱包** - 使用 SLIP77 描述符导入 Liquid 观察钱包
- **Lightning 支付** - 支付 Bolt 11 和 Bolt 12 发票或闪电地址
- **Lightning 发票** - 生成 Bolt 11 发票
- **链上兑换** - 在 L1 和 L2 之间轻松兑换
- **选币控制** - 为兑换和支付选择特定 UTXO
- **BIP329 标签** - 支持 Ark、Spark 和 Liquid 交易标签

### 隐私与安全
- **默认离线** - 应用启动时零外部连接
- **内置 Tor** - 原生集成 Tor，无需 Orbot 或外部代理
- **PIN 与生物识别** - 支持可配置锁定时间的 PIN 和生物识别
- **胁迫 PIN** - 设置胁迫 PIN，打开诱饵钱包
- **擦除 PIN** - 设置备用 PIN，静默擦除设备上的所有钱包数据
- **自动擦除** - 设置解锁失败次数阈值，达到后自动不可逆地擦除所有应用数据
- **清空剪贴板** - 应用锁定或关闭时自动清空剪贴板的选项
- **伪装模式** - 将 Ibis 伪装成计算器应用
- **隐私开关** - 隐藏所有钱包金额和余额
- **擦除历史** - 在本地擦除特定钱包的交易或全部历史
- **钱包锁** - 独立锁定特定钱包
- **强化元数据** - 经处理的日志和界面错误，减少操作系统可见的元数据

### 连接与服务器
- **自定义服务器** - 连接自己的 Electrum、区块浏览器和费率估算服务器
- **NFC 支持** - 通过 NFC 轻触广播和接收付款请求
- **更新通知** - 新版本更新通知开关
- **Bitcoin URI 处理** - 注册为 `bitcoin:` 链接的处理程序

### 本地化
- **语言** - 英语、西班牙语、俄语、葡萄牙语（巴西）、法语、德语、日语和简体中文
- **字体** - 多种字体设置

### 备份与恢复
- **完整加密备份** - 备份和恢复整个应用状态，包括钱包、设置和标签

## 构建

需要 Android Studio 和 JDK 17。

```bash
./gradlew :app:assembleDebug      # Debug
./gradlew :app:assembleRelease    # Release
./gradlew testDebugUnitTest       # 测试
./gradlew jacocoUnitTestReport    # 生成覆盖率报告
```

**最低 SDK：** 26 (Android 8.0) | **目标 SDK：** 36 | **ARM**（armeabi-v7a、arm64-v8a）

## 漏洞赏金

> [!IMPORTANT]
> **漏洞赏金计划因资金不足目前处于暂停状态。**欢迎提交报告并会予以致谢，但在计划恢复之前不会支付货币奖励。

我们为可能导致用户资金被未经授权损失的 Ibis Wallet 严重漏洞提供 1000 美元（以 BTC 支付）。

赏金范围内的漏洞是指提供切实可行的现实攻击路径，导致用户资金被盗或永久损失的漏洞（例如远程攻破钱包、助记词泄露、交易篡改等）。

Ibis 是测试版软件，面向经验丰富的比特币用户。离线助记词备份是预期的标准做法：仅通过助记词恢复即可完全挽回、且不涉及第三方盗窃的问题会予以承认，但不在奖励范围内。

较低严重性问题、无实际利用路径的理论漏洞，或不导致资金损失的发现会予以承认，但一般不在货币奖励范围内。

漏洞赏金颁发：[2026 年 8 月 15 日](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## 免责声明

Ibis 由最新前沿 AI 模型编写和审计。

审计：[2026 年 10 月 29 日](https://github.com/aeonBTC/IbisWallet/releases/tag/v5.1.0-beta) · [2026 年 8 月 15 日](https://github.com/aeonBTC/IbisWallet/releases/tag/v4.7.0-beta)

## 捐赠

由于这个钱包最初是为自己做的，我不期待捐赠。不过 token 并不便宜。如果您想表示感谢，欢迎捐赠。

<img width="217" height="200" alt="image" src="https://github.com/user-attachments/assets/ea7993d0-ce33-4578-b534-5ea86cd74038" />

```bash
bc1qwjvn8qf27g6fesna35828u4wjpeh325rlu05a8
```


## 许可证

开源。详情见 [LICENSE](../LICENSE)。
