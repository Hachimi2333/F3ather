# F3ather

Minecraft 1.21.1 · NeoForge 21.1.172–21.1.x · Java 21

## 玩家指南

将 `build/libs` 生成的 F3ather JAR 放进客户端的 `mods` 目录。JAR 已包含设置页框架和字体。

- `F3` 打开调试界面。F3ather 模式使用非像素字体和紧凑面板，默认显示帧率、坐标、区块内位置、朝向和群系。传统模式显示原版 F3。
- `F3 + J` 或 **模组 → F3ather → 配置** 打开设置。左侧切换模式和改键，右侧开启、关闭和排列新版 HUD 的项目。设置保存在 `config/f3ather-client.toml`，旧版预设和开关会自动迁移。
- `F3 + Page Up / Page Down` 翻阅新版 HUD，`F3 + 1/2/3` 仍控制原版图表。新版 HUD 和设置页随游戏切换简体中文和英文。
- 在世界里打开一次 F3ather HUD，就能在设置中看到其他模组通过 NeoForge DebugText 添加的项目。项目显示可识别的模组名、文字样例和独立开关。第三方文本沿用提供方的语言。

## Player guide

Install the F3ather JAR from `build/libs` in the client's `mods` folder. It bundles its settings framework and font.

- Press `F3` to open the overlay. F3ather mode uses a compact layout and a custom font renderer. Its initial view shows frame rate, coordinates, position within a section, facing, and biome. Traditional mode uses Minecraft's F3 display.
- Press `F3 + J` or open **Mods → F3ather → Config**. The left pane selects the mode and shortcut. The right pane toggles and reorders F3ather entries. Settings live in `config/f3ather-client.toml`. Existing presets and overrides migrate on first load.
- Use `F3 + Page Up / Page Down` to browse the new HUD. `F3 + 1/2/3` retain the native charts. The HUD and settings follow the game's English or Simplified Chinese language selection.
- Open the F3ather HUD in a world to discover text supplied by other mods through NeoForge DebugText. Each discovered entry shows a sample and a mod name when it can be identified. The original mod supplies the language of its text.

## 构建 / Build

将 `JAVA_HOME` 指向 JDK 21。Set `JAVA_HOME` to a JDK 21 installation.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat "-PneoVersion=21.1.172" runClient
```

On macOS or Linux, use `./gradlew`. The JAR in `build/libs` embeds OpenUI MC 0.0.16. The build verifies the release artifact's SHA-256. The bundled F3ather Sans face is a regular-weight instance of Noto Sans SC from the Sans2.004 release. Both upstream licenses are in the JAR under `META-INF`.

构建默认使用 Gradle 和 Maven 镜像。NeoForge 构件来自官方仓库。使用 `-PuseChinaMirrors=false` 可切换 Gradle 插件和 Maven Central 来源。Minecraft 资源对象也可通过环境变量 `NFRT_ASSET_REPOSITORY` 指定镜像，例如 `https://bmclapi2.bangbang93.com/assets/`。

## 兼容与性能测试 / Compatibility and performance

`gradlew test` 验证逐帧可编辑列表、附加项来源区分、排序、精简调试范围和条目数量上限。测试客户端用成品 JAR 启动，并加载两个独立测试模组。它们每帧合计添加 99 行第三方文本，其中两行完全相同，同时也会改写和移动已有内容。

```powershell
.\gradlew.bat runFixtureClient -PfixtureAuto
.\gradlew.bat runFixtureClient -PfixtureAuto -PfixtureVisual
.\gradlew.bat runFixtureClient -PfixtureBaseline -PfixtureAuto
```

自动模式使用隔离的测试世界，先测 F3ather，再测传统模式。日志在 `build/fixture/logs/latest.log`，`F3ATHER_MEASUREMENT` 记录两列原版采集频率、HUD CPU 耗时和帧间隔。可加 `-PfixtureVisual` 验证设置入口、精简规则和关闭 HUD 时停止采集，并保存中英文界面截图。

要验证多人环境，先运行下面的第一条命令，等本机回环服务器报告就绪，再在第二个终端运行第二条命令。测试客户端使用独立目录 `build/fixture-network`，并验证远程服务器统计、未加载区块、维度切换和服务器强制精简调试。

```powershell
.\gradlew.bat runFixtureClient -PfixtureAuto '-PfixtureHost=25578'
.\gradlew.bat runFixtureClient '-PfixtureServer=127.0.0.1:25578'
```
