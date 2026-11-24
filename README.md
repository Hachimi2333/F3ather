# F3ather

Minecraft 1.21.1 · NeoForge 21.1.172–21.1.x · Java 21

## 玩家指南

从 `build/libs` 取出 F3ather JAR，放入客户端的 `mods` 目录。

- `F3` 打开调试 HUD，文字从两侧滑入，标签栏从上方滑入，再按一次原路退场。标签栏闲置 4 秒后收起，顶部文字随之上移。按 `Tab` 唤回标签栏并切到下一页。模组页在当前世界检测到第三方通过文字采集或 NeoForge DebugText 新增的行后出现。
- 模组页和自定义页有多页时，按 `F3 + Page Up / Page Down` 翻页。`F3 + 1/2/3` 仍控制原版图表。
- 按 `F3 + 0` 或从 **模组 → F3ather → 配置** 打开设置。左栏提供 1 Hz、5 Hz、10 Hz、20 Hz 和无限制刷新率，也可切换兼容模式和禁用动画。右栏管理标签页。禁用动画后，标签栏仍按时收起。兼容模式恢复原版 F3，`F3 + 0` 仍可访问设置。
- 新建自定义页后，按 `/` 或点击「添加」搜索条目。拖动条目可跨四角区域移动或排序。改动自动保存到 `config/f3ather-tabs.toml`。在标题画面打开编辑器时使用格式与原版一致的示例数据。

## Player guide

Install the F3ather JAR from `build/libs` in your client's `mods` directory.

- Press `F3` to slide in the debug overlay and press it again to slide it out. After four seconds of inactivity, the header slides away and the top rows move up. Press `Tab` to bring back the header and switch to the next tab. The Mods tab appears when another mod adds a row during text collection or the NeoForge DebugText event.
- Use `F3 + Page Up / Page Down` for long Mods or custom tabs. The native debug charts still use `F3 + 1/2/3`.
- Use `F3 + 0` or **Mods → F3ather → Config** to manage tabs, choose 1 Hz, 5 Hz, 10 Hz, 20 Hz, or Unlimited, disable animations, or restore the native F3 through Compatibility Mode. The header still auto-hides with animations disabled.
- In a custom tab, press `/` or select **Add** to search. Drag entries between corners or reorder them within a corner. Changes save to `config/f3ather-tabs.toml`. The editor uses sample values on the title screen.

## 构建与实机验收 / Build and in-game checks

将 `JAVA_HOME` 指向本机的 JDK 21。在 macOS 和 Linux 上使用 `./gradlew`。

```powershell
.\gradlew.bat "-PneoVersion=21.1.172" build fixtureClasses fixturePeerClasses
.\gradlew.bat build fixtureClasses fixturePeerClasses
.\gradlew.bat "-PneoVersion=21.1.172" runClient
.\gradlew.bat runClient
```

在两个版本上分别检查以下项目。

- 从游戏内和标题界面进入设置，分别检查世界和全景背景模糊。切换中英文和 GUI 缩放，确认左上角 Logo、方角控件和提示、刷新率五档及选中即保存。
- 编辑器工具条居中，四角可拖放。用 `/` 或「添加」打开搜索，键入和粘贴中英文，检查光标、筛选、回车和 Esc。重启后确认设置和自定义标签已保存。
- F3 左右文字和顶栏进场及退场。快速开关时从当前位置反向，闲置 4 秒收起后的顶栏在退场时保持隐藏，F1 立即结束退场。检查 `Tab` 唤回并切页、翻页唤回页码、原版图表、多人按键和兼容模式。启用「禁用动画」后确认 F3 即时开关，顶栏仍按时收起。
- 使用 Sodium 0.8.13 检查「模组」页的帧率百分位、堆外内存、渲染器统计和编辑器中的 Sodium 搜索结果。检查「原版」仍显示完整文字。卸载 Sodium 和其他调试文字模组后，进入世界确认「模组」页隐藏。

The JAR embeds OpenUI MC 0.0.16. The build verifies its SHA-256. Gradle and Maven mirrors are used by default, while NeoForge artifacts come from the official repository. Use `-PuseChinaMirrors=false` to disable the Gradle and Maven Central mirrors. Minecraft assets can use an `NFRT_ASSET_REPOSITORY` mirror such as `https://bmclapi2.bangbang93.com/assets/`.

## 兼容性与性能夹具 / Compatibility and performance fixtures

测试客户端加载两个独立模组，在采集阶段插入两行文字，并通过事件追加 99 行调试文字、改写、移动和删除原版行。自动模式测量原版 Tab 的 10 Hz 和无限制档，以及兼容模式。日志 `build/fixture/logs/latest.log` 中的 `F3ATHER_MEASUREMENT` 记录采样频率、每列文字绘制和整个 HUD 的 CPU 耗时，以及帧间隔。可选视觉检查保存设置、编辑器和模组页的截图。

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
