<p align="center">
  <img src="src/main/resources/f3ather-logo.png" alt="F3ather 羽毛标志" width="128">
</p>

<h1 align="center">F3ather</h1>

<p align="center">为 Minecraft 提供轻量、可自定义的 F3 调试界面。</p>

<p align="center">
  <a href="https://github.com/Hachimi2333/F3ather/releases">下载</a> ·
  <a href="https://github.com/Hachimi2333/F3ather/issues">问题反馈</a> ·
  <a href="README.md">English</a>
</p>

## 功能

- 在「精简」「性能」「世界」「模组」和「原版」之间切换。当其他模组添加调试行时会显示「模组」页，其中包含可识别的 Sodium 统计。
- 搜索并添加调试条目，在四角预览中拖放排版，创建自己的标签页。
- 保留 Minecraft 的字体、逐行底色、调试图表和服务器强制精简调试。F3ather 缓存字形绘制指令，并按设定频率采集调试数据。
- 刷新率提供 **1 Hz、5 Hz、10 Hz、20 Hz 和无限制**。默认 10 Hz，无限制档每帧采集。
- 标签栏闲置四秒后自动收起，也可设置为始终隐藏。设置中还可关闭动画，或开启兼容模式恢复原版 F3 行为。

## 安装

F3ather 是客户端模组，适用于 **Minecraft 1.21.1**、**NeoForge 21.1.172 及同属 21.1 系列的更新版本**、**Java 21**。

1. 从 [Releases](https://github.com/Hachimi2333/F3ather/releases) 下载 JAR。
2. 将 JAR 放入 Minecraft 实例的 `mods` 文件夹，然后启动 NeoForge。

设置界面使用的 OpenUI MC 已随 JAR 打包。

## 操作

| 快捷键 | 功能 |
| --- | --- |
| `F3` | 打开或关闭调试界面 |
| F3 打开时按 `Tab` | 切换到下一个已启用标签，隐藏标签栏时也可使用 |
| `F3 + Page Up` / `F3 + Page Down` | 在多页的模组或自定义标签中翻页 |
| `F3 + 1` / `F3 + 2` / `F3 + 3` | 开关原版调试图表 |
| `F3 + 0` | 打开 F3ather 设置 |
| 编辑标签时按 `/` | 搜索要添加的调试条目 |

也可从 **模组 → F3ather → 配置** 打开设置。自定义标签布局和偏好会自动保存到 `config/f3ather-tabs.toml`。

## 从源码构建

将 `JAVA_HOME` 指向本机的 JDK 21，然后使用 Gradle Wrapper。默认以 NeoForge 21.1.267 构建。

```sh
./gradlew build
./gradlew runClient
```

Windows 下用 `.\gradlew.bat` 替换 `./gradlew`。成品 JAR 位于 `build/libs/`。添加 `-PneoVersion=21.1.172` 可用最低支持版本构建或运行客户端。

构建会校验随包分发的 OpenUI MC 0.0.16。Gradle 和 Maven Central 默认使用镜像，加上 `-PuseChinaMirrors=false` 可关闭。NeoForge 构件从官方仓库获取。

### 测试

```sh
./gradlew build fixtureClasses fixturePeerClasses
./gradlew -PneoVersion=21.1.172 build fixtureClasses fixturePeerClasses
./gradlew runFixtureClient -PfixtureAuto
./gradlew runFixtureClient -PfixtureAuto -PfixtureVisual
./gradlew runFixtureClient -PfixtureBaseline -PfixtureAuto
```

两个测试模组会在调试文字采集流程的不同阶段追加和修改文字。自动模式将采样与耗时写入测试实例的 `logs/latest.log`，可搜索 `F3ATHER_MEASUREMENT`。视觉检查会保存 HUD、设置和标签编辑器的截图。

多人测试需先启动本机回环服务器，等服务器就绪后，再从另一个终端启动访客端。

```sh
./gradlew runFixtureClient -PfixtureAuto -PfixtureHost=25578
./gradlew runFixtureClient -PfixtureServer=127.0.0.1:25578
```

在两版 NeoForge 上检查 F3 动画和图表快捷键、隐藏标签栏、中英文设置及不同 GUI 缩放、编辑器输入和拖放，以及安装和卸载 Sodium 时的「模组」页。

## 参与贡献

开发约定见 [AGENTS.md](AGENTS.md)。反馈问题时，请附上 Minecraft、NeoForge 和 F3ather 版本、复现步骤、相关日志及其他已安装模组。[提交问题](https://github.com/Hachimi2333/F3ather/issues)。

## 许可证

F3ather 采用 [MIT 许可证](LICENSE)。随包分发的 OpenUI MC 许可证见[第三方声明](src/main/resources/META-INF/LICENSE-OpenUI-MC.txt)。
