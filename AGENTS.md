# F3ather 开发指南

- 目标平台为 Minecraft 1.21.1、NeoForge 21.1.172 至 21.1.x 和 Java 21。默认使用 21.1.267 构建。
- 从 NeoForge 配置入口修改客户端显示设置。界面文案在 `en_us` 和 `zh_cn` 同步维护。
- 保留原版 F3 图表、精简调试信息和 NeoForge 的逐帧 DebugText 事件。每帧传给事件的文本列表都可以由监听器修改。避免在渲染热路径读写磁盘或积累无上限的条目。
- 使用 Gradle Wrapper 验证，Windows 运行 `./gradlew.bat build`，其他系统运行 `./gradlew build`。变更 F3 挂点时，分别以默认及 `-PneoVersion=21.1.172` 启动客户端验证。
- 境内构建默认使用 Gradle 和 Maven 镜像。NeoForge 构件保留官方来源。更新 Wrapper 时一并更新官方分发包校验值。
- 按功能拆分提交，使用 Conventional Commits 标题与非空正文。正文说明动机、改动和实际验证情况。
