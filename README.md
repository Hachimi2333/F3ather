# F3ather

Minecraft 1.21.1 · NeoForge 21.1.172–21.1.x · Java 21

## 玩家指南

从 `build/libs` 取出 F3ather JAR，放入客户端的 `mods` 目录。

- `F3` 打开调试 HUD。按 `Tab` 依次切换精简、性能、世界、模组、原版和已启用的自定义页。模组页在当前世界检测到其他模组通过 NeoForge DebugText 追加文字后出现。
- 模组页和自定义页有多页时，按 `F3 + Page Up / Page Down` 翻页。`F3 + 1/2/3` 仍控制原版图表。
- 按 `F3 + 0` 或从 **模组 → F3ather → 配置** 打开设置。可开关标签页、调整文本刷新率或开启兼容模式。兼容模式恢复原版 F3，`F3 + 0` 仍可访问设置。
- 新建自定义页后，按 `/` 或点击「添加」搜索条目。拖动条目可跨四角区域移动或排序。改动自动保存到 `config/f3ather-tabs.toml`。在标题画面打开编辑器时使用格式与原版一致的示例数据。

## Player guide

Install the F3ather JAR from `build/libs` in your client's `mods` directory.

- Open the overlay with `F3` and cycle through enabled tabs with `Tab`. The Mods tab appears after another mod adds a NeoForge DebugText line in the current world.
- Use `F3 + Page Up / Page Down` for long Mods or custom tabs. The native debug charts still use `F3 + 1/2/3`.
- Use `F3 + 0` or **Mods → F3ather → Config** to manage tabs, change the text refresh rate, or restore the native F3 through Compatibility Mode.
- In a custom tab, press `/` or select **Add** to search. Drag entries between corners or reorder them within a corner. Changes save to `config/f3ather-tabs.toml`. The editor uses sample values on the title screen.

## 构建与实机验收 / Build and in-game checks

将 `JAVA_HOME` 指向本机的 JDK 21。在 macOS 和 Linux 上使用 `./gradlew`。

```powershell
.\gradlew.bat "-PneoVersion=21.1.172" build fixtureClasses fixturePeerClasses
.\gradlew.bat build fixtureClasses fixturePeerClasses
.\gradlew.bat "-PneoVersion=21.1.172" runClient
.\gradlew.bat runClient
```

两个版本分别确认原版数据行和图表、`Tab` 切换和多页翻页、兼容模式下的多人玩家列表、F3ather 图标，以及自定义页的搜索、跨区拖放和重启后的保存结果。主设置和编辑器在游戏语言为英语和简体中文时各检查一次。

The JAR embeds OpenUI MC 0.0.16. The build verifies its SHA-256. Gradle and Maven mirrors are used by default, while NeoForge artifacts come from the official repository. Use `-PuseChinaMirrors=false` to disable the Gradle and Maven Central mirrors. Minecraft assets can use an `NFRT_ASSET_REPOSITORY` mirror such as `https://bmclapi2.bangbang93.com/assets/`.

## 兼容性与性能夹具 / Compatibility and performance fixtures

测试客户端加载两个独立模组，每帧追加 99 行调试文字，包括两行相同文字，还会改写、移动和删除原版行。自动模式测量原版 Tab 的 10 Hz 和每帧档，以及兼容模式。日志 `build/fixture/logs/latest.log` 中的 `F3ATHER_MEASUREMENT` 记录采样频率、每列文字绘制和整个 HUD 的 CPU 耗时，以及帧间隔。可选视觉检查保存设置、编辑器和模组页的截图。

```powershell
.\gradlew.bat runFixtureClient -PfixtureAuto
.\gradlew.bat runFixtureClient -PfixtureAuto -PfixtureVisual
.\gradlew.bat runFixtureClient -PfixtureBaseline -PfixtureAuto
```

多人夹具验证远程服务器统计、未加载区块、维度切换和服务器强制精简调试。先启动本机回环服务器，等日志显示就绪后再启动访客端。

```powershell
.\gradlew.bat runFixtureClient -PfixtureAuto "-PfixtureHost=25578"
.\gradlew.bat runFixtureClient "-PfixtureServer=127.0.0.1:25578"
```
