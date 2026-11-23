# F3ather

Minecraft 1.21.1 · NeoForge 21.1.172–21.1.x · Java 21

F3ather 可以精简 F3 调试屏幕，减少文字采集频率和绘制行数。把 `f3ather-0.1.0.jar` 放进客户端的 `mods` 目录即可使用，服务器可以保持原有配置。

## 玩家指南

从 **模组 → F3ather → 配置** 打开设置。

| 预设 | 显示内容 |
| --- | --- |
| 轻量（默认） | 帧率、坐标、朝向和生物群系 |
| 常用 | 加上方块坐标、区块、维度、光照和准星目标 |
| 原版 | 完整的左右两列文本，包含其他模组追加的文字 |
| 自定义 | 逐项控制显示，可从任一内置预设重置 |

进入世界后打开一次 F3，配置页就会列出发现的附加条目。附加内容按左右列和稳定标签识别，标签之后的数值可以持续变化。同列同名标签共用开关，缺少稳定标签的文字归入该列的「其他附加内容」。自动适配覆盖 Minecraft F3 左右两列。完整替换 F3 界面或单独绘制 HUD 的模组需要专门适配。

刷新方式独立于预设。默认的「限频」每 100 毫秒最多采集一次原版两列文字，第三方的 NeoForge DebugText 事件仍逐帧触发。「逐帧」让原版文字也逐帧更新。F3 图表继续通过 `F3+1`、`F3+2`、`F3+3` 控制。设置保存在 `config/f3ather-client.toml`。

## Player guide

Install the JAR in the client’s `mods` folder and open **Mods → F3ather → Config**. Light is the default preset and shows FPS, coordinates, facing, and biome. Common adds block and chunk information, dimension, light, and targeted block, fluid, and entity data. Original shows the complete text columns. Select Custom to edit individual entries and optionally reset them from a built-in preset.

Open F3 in a world to discover additional entries. Stable labels are grouped by column and label. Other text in each column shares an “Other additional text” switch. Automatic discovery covers additions to Minecraft’s two F3 columns. HUDs drawn independently and full F3 replacements need their own integrations.

The default refresh mode samples Minecraft’s text at up to 10 Hz. Every Frame restores the normal sample rate. The NeoForge DebugText event still runs every frame. The stock F3 chart shortcuts remain available. Settings are saved to `config/f3ather-client.toml`.

## 构建 / Build

将 `JAVA_HOME` 指向 JDK 21。Set `JAVA_HOME` to a JDK 21 installation.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat "-PneoVersion=21.1.172" runClient
```

在 macOS/Linux 上，将 `.\gradlew.bat` 换成 `./gradlew`。The JAR is generated in `build/libs`.

构建默认使用华为云 Gradle 镜像和阿里云 Gradle Plugin、Maven Central 镜像。使用 `-PuseChinaMirrors=false` 可切回官方 Maven 和插件仓库。若需要切换 Gradle 分发地址，修改 `gradle-wrapper.properties` 中的 `distributionUrl` 为 `https://services.gradle.org/distributions/gradle-9.2.1-bin.zip`，继续使用相同的官方 SHA-256。NeoForge 专用构件使用官方 Maven。

Minecraft 资源对象可通过 BMCLAPI 下载。运行前在 PowerShell 中设置以下环境变量，恢复官方资源地址时可移除该变量。Use the same variable with `export` in a POSIX shell.

```powershell
$env:NFRT_ASSET_REPOSITORY = 'https://bmclapi2.bangbang93.com/assets'
.\gradlew.bat downloadAssets
```

## 兼容与性能测试 / Compatibility and performance

`gradlew test` 验证采集间隔、模式切换、缓存失效、原版分组和第三方发现逻辑。独立测试模组使用真实的 NeoForge 事件，在两列加入 97 行附加文本，并改写、删除和重排文本。测试模组仅由 `runFixtureClient` 加载。

```powershell
.\gradlew.bat runFixtureClient
.\gradlew.bat runFixtureClient -PfixtureBaseline
.\gradlew.bat runFixtureClient "-PfixtureWorld=f3ather-test" -PfixtureAuto
.\gradlew.bat runFixtureClient "-PfixtureServer=127.0.0.1:25565" -PfixtureAuto
```

在测试客户端创建文件夹名为 `f3ather-test` 的测试世界，或用 `fixtureServer` 连接测试服务器。打开 F3 后，测试模组预热三秒，再测量八秒，并在 `runs/fixture/logs/latest.log` 输出 `F3ATHER_MEASUREMENT`。其中包含每列每秒采集次数、每帧绘制行数和帧间隔。带 `fixtureAuto` 的运行会自动开启 F3 并在采样结束后退出。对比时使用同一世界、视角和视频设置，分别记录只有测试模组、原版预设和轻量预设的数据。
