# F3ather Development Guide

- Target Minecraft 1.21.1, NeoForge 21.1.172 through 21.1.x, and Java 21. The default build uses NeoForge 21.1.267.
- Use the locally installed JDK 21. Keep additional build tools and temporary files in the repository's Git-ignored `.gradle/` or `build/` directories.
- Expose client display settings through the NeoForge configuration entry point. Update `en_us` and `zh_cn` translations together.
- Preserve vanilla F3 charts, reduced debug information, and NeoForge's per-frame `DebugText` event. Give listeners mutable text lists for each frame. Avoid disk I/O and unbounded collections on the rendering hot path.
- Verify changes with the Gradle Wrapper. Run `.\gradlew.bat build` on Windows or `./gradlew build` elsewhere. When changing F3 hooks, verify the client with both the default NeoForge version and `-PneoVersion=21.1.172`.
- Builds use Gradle and Maven mirrors by default for mainland China. Resolve NeoForge artifacts from the official repository. Update the official distribution checksum whenever you update the Gradle Wrapper.
- Group changes into focused Conventional Commits with non-empty bodies. Describe the motivation, the changes, and the verification actually performed.

## GitHub Release Template

- Use `v<version>` for the tag, `F3ather v<version>` for the release title, and `f3ather-neoforge-<minecraftVersion>-<version>.jar` for the release asset. Match the asset name to the Gradle build output.
- Write the release notes in English. Keep the section order below and base the highlights on user-facing changes since the previous release.
- Verify the installation requirements and bundled dependencies against the current build before writing the installation paragraph.

```markdown
# F3ather v<version>

<One short paragraph describing F3ather and its main features.>

## Highlights

- <User-facing change and its effect.>
- <Additional user-facing changes, one per bullet.>

## Installation

Requires **Minecraft <minecraftVersion>, NeoForge <minimumNeoVersion> or later in the <neoSeries> series, and Java <javaVersion>**. Place `f3ather-neoforge-<minecraftVersion>-<version>.jar` in your client's `mods` folder. OpenUI MC is bundled.
```
