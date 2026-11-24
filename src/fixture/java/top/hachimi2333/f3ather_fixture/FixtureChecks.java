package top.hachimi2333.f3ather_fixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;
import top.hachimi2333.F3ather;
import top.hachimi2333.config.ClientConfig;
import top.hachimi2333.config.F3atherScreen;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.config.TabEditorScreen;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

final class FixtureChecks {
    private static OverlaySettings original;

    private FixtureChecks() {}

    static void prepare() {
        original = ClientConfig.current();
        measure(false, 10);
    }

    static void measure(boolean compatibility, int rate) {
        ClientConfig.save(new OverlaySettings(rate, compatibility, true, Set.of(BuiltIn.VANILLA), java.util.List.of()));
    }

    static void restore() {
        ClientConfig.save(original);
    }

    static void enableAll() {
        ClientConfig.save(OverlaySettings.DEFAULT);
    }

    static void openEditor() {
        var tab = Tab.create("Fixture")
                .move("vanilla:xyz", Region.TOP_LEFT, 0)
                .move("vanilla:biome", Region.BOTTOM_LEFT, 0)
                .move("vanilla:target_block", Region.TOP_RIGHT, 0)
                .move("vanilla:memory", Region.BOTTOM_RIGHT, 0);
        ClientConfig.save(ClientConfig.current().withTab(tab));
        Minecraft.getInstance().setScreen(new TabEditorScreen(new F3atherScreen(null), tab.id()));
    }

    static void selectTab(Minecraft mc, String id) {
        for (int i = 0; i < 6 && !id.equals(F3ather.HUD.activeTab()); i++) {
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_PRESS);
            key(mc, GLFW.GLFW_KEY_TAB, GLFW.GLFW_RELEASE);
        }
        if (!id.equals(F3ather.HUD.activeTab())) throw new IllegalStateException("Could not select tab " + id);
    }

    static void verifyAddons() {
        long discovered = F3ather.ADDONS.entries().stream().filter(entry -> entry.source().id().equals("f3ather_fixture")).count();
        if (discovered < 98) throw new IllegalStateException("Missing fixture attribution: " + discovered);
        var owners = F3ather.ADDONS.entries().stream().filter(entry -> entry.name().equals("Shared status"))
                .map(entry -> entry.source().id()).collect(Collectors.toSet());
        if (!owners.equals(Set.of("f3ather_fixture", "f3ather_fixture_peer"))) {
            throw new IllegalStateException("Shared label attribution: " + owners);
        }
    }

    static void openSettings() {
        var container = ModList.get().getModContainerById("f3ather").orElseThrow();
        var factory = container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
        Minecraft.getInstance().setScreen(factory.createScreen(container, null));
    }

    static void switchLanguage(Minecraft mc, String language) {
        mc.getLanguageManager().setSelected(language);
        mc.reloadResourcePacks();
    }

    static void verifyShortcuts(Minecraft mc) {
        var configured = ClientConfig.current();
        pressDebugChord(mc, GLFW.GLFW_KEY_0);
        requireSettings(mc);
        key(mc, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
        if (mc.screen != null || !ClientConfig.current().equals(configured)) {
            throw new IllegalStateException("Opening settings changed the current configuration");
        }

        ClientConfig.save(configured.withCompatibility(true));
        pressDebugChord(mc, GLFW.GLFW_KEY_0);
        requireSettings(mc);
        key(mc, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
        ClientConfig.save(configured);
    }

    private static void requireSettings(Minecraft mc) {
        if (!(mc.screen instanceof F3atherScreen)) throw new IllegalStateException("Debug chord did not open settings");
    }

    static void pressDebugChord(Minecraft mc, int key) {
        key(mc, GLFW.GLFW_KEY_F3, GLFW.GLFW_PRESS);
        key(mc, key, GLFW.GLFW_PRESS);
        key(mc, key, GLFW.GLFW_RELEASE);
        key(mc, GLFW.GLFW_KEY_F3, GLFW.GLFW_RELEASE);
    }

    private static void key(Minecraft mc, int key, int action) {
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, GLFW.glfwGetKeyScancode(key), action, 0);
    }

    static void capture(String name) {
        var mc = Minecraft.getInstance();
        var folder = mc.gameDirectory.toPath().resolve("screenshots");
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            Files.createDirectories(folder);
            image.writeToFile(folder.resolve(name + ".png"));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        if (name.equals("settings-zh") && !Component.translatable("f3ather.screen.title").getString().contains("设置")) {
            throw new IllegalStateException("Simplified Chinese resources did not reload");
        }
    }
}
