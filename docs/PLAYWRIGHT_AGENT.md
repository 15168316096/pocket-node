# Pocket Node APK + Playwright Agent 集成方案

这个目录提供一个最小可运行的 agent 自动化骨架，用 Playwright 的 Android `_android` API 通过 ADB 连接真机或模拟器，安装并启动 Pocket Node APK，保存关键截图，并为后续 onboarding、钱包创建、同步状态、发送/接收等流程留出扩展点。

## 结论

Playwright 可以用于这类集成，但边界要明确：

- Playwright 官方 Android 能力仍是 experimental，依赖 ADB，支持安装 APK、启动应用、截图、点击、输入、读取 Android widget 信息，也支持 Chrome / WebView。
- 如果 Pocket Node 页面是原生 Android UI，自动化可通过 Android selector、文本、resource id、坐标等方式推进；要做长期稳定回归，建议在 APK 中补齐稳定的 accessibility label / test tag。
- 如果要覆盖系统权限、Keystore/PIN/生物识别、二维码扫描、网络波动、后台运行、崩溃恢复等移动端深层场景，建议把 Playwright 作为 agent 编排层，并把 Appium/UIAutomator/ADB 命令作为 native 操作补充。

Pocket Node 当前公开描述是 Android CKB light wallet：APK 来自 GitHub Release，Android 端内嵌 Rust CKB light client，通过 JNI 本地同步、查余额、广播交易；官网也说明它支持多钱包、HD 子账户、地址簿、Nervos DAO、硬件密钥、PIN、四种同步模式和应用内更新。

## 运行方式

先准备 Android 设备：

1. 启动 Android emulator 或连接真机。
2. 打开 USB debugging。
3. 运行 `adb devices`，确认设备是 `device` 状态。
4. 让设备保持亮屏；截图依赖设备处于 awake 状态。

安装依赖并下载 release APK：

```bash
npm install
npm run apk:download
```

默认会下载 `https://github.com/RaheemJnr/pocket-node/releases/download/v1.7.1/PocketNode-v1.7.1.apk` 到 `artifacts/PocketNode-v1.7.1.apk`。如需覆盖来源或输出路径：

```bash
POCKET_NODE_APK_URL=https://example.com/PocketNode.apk \
APK_PATH=artifacts/PocketNode.apk \
npm run apk:download
```

下载后跑 smoke：

```bash
APK_PATH=artifacts/PocketNode-v1.7.1.apk \
POCKET_NODE_PACKAGE=com.pocketnode.app \
npm run android:smoke
```

也可以使用 CI 同款组合命令，自动下载后安装并启动：

```bash
POCKET_NODE_PACKAGE=com.pocketnode.app npm run android:ci
```

如果已经安装 APK，可以省略 `APK_PATH`：

```bash
POCKET_NODE_PACKAGE=com.pocketnode.app npm run android:smoke
```

多设备时指定序列号：

```bash
DEVICE_SERIAL=emulator-5554 APK_PATH=/path/to/pocket-node.apk npm run android:smoke
```

截图会写入 `screenshots/`。

## CI 模拟器验证

仓库包含 `.github/workflows/android-smoke.yml`，会在 GitHub Actions 的 `ubuntu-latest` runner 上启用 KVM，使用 `reactivecircus/android-emulator-runner@v2` 启动一个 headless Android x86_64 模拟器，然后执行：

```bash
npm run typecheck
npm run android:ci
```

`android:ci` 会先下载 Pocket Node APK，再通过 Playwright Android API 安装、启动并截图。CI 结束后会上传 `screenshots/` 作为 artifact，便于检查当前 UI 状态。

## Agent 架构

推荐拆成三层：

- `Agent planner`：把自然语言任务转成测试目标，例如“安装最新 APK，创建测试钱包，确认进入同步页并保存截图”。
- `Mobile driver`：当前 scaffold 中的 `src/pocket-node-agent.ts`，负责连接设备、安装 APK、启动包名、点击/输入/截图。
- `Verifier`：读取截图、UI tree、日志、CKB light client 状态，给出 pass/fail 和失败原因。

后续可以把 `src/pocket-node-agent.ts` 扩展为命令式接口：

```bash
npm run android:smoke -- --scenario create-wallet
npm run android:smoke -- --scenario sync-status
npm run android:smoke -- --scenario receive-address
```

## 建议的首批场景

- APK install + launch：安装 release APK，启动应用，确认首屏存在。
- Onboarding guard：首次进入必须完成助记词备份/确认，不能绕过。
- Import wallet：导入测试网助记词，设置 PIN，进入 dashboard。
- Sync mode：选择 Recent 或自定义高度，确认 light client 状态从 starting 进入 syncing / synced。
- Receive address：打开 receive 页，确认地址文本和 QR 图存在。
- Network switch：Mainnet/Testnet 切换后应用重启提示清晰，不表现为崩溃。
- Upgrade smoke：旧版本安装后覆盖安装新 APK，钱包数据仍可打开或给出明确恢复路径。

## 需要 Pocket Node APK 配合的测试钩子

为了让自动化长期稳定，建议 APK 增加：

- 关键按钮、输入框、状态文本的 accessibility label 或 resource id。
- 测试网 faucet / mock balance 的可控入口，避免依赖真实资产。
- 跳过真实生物识别的 debug build 开关，保留 PIN 路径测试。
- 可导出的 light client 状态字段：network、tip height、sync mode、peer count、last error。
- 禁止真实主网转账的 CI/test build 防护。

## 参考

- Pocket Node 官网：`https://www.pocket-node.com/`
- Pocket Node GitHub：`https://github.com/RaheemJnr/pocket-node`
- Playwright Android API：`https://playwright.dev/docs/api/class-android`
- Playwright AndroidDevice API：`https://playwright.dev/docs/api/class-androiddevice`

