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
import top.hachimi2333.overlay.OverlaySettings.Mode;
import top.hachimi2333.overlay.VanillaEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

final class FixtureChecks {
    private static OverlaySettings original;

    private FixtureChecks() {}

    static void prepare() {
        original = ClientConfig.current();
        ClientConfig.save(OverlaySettings.DEFAULT);
    }

    static void traditional() {
        ClientConfig.save(new OverlaySettings(Mode.TRADITIONAL, "J", original.enabled(), original.order(), original.legacyAddons()));
    }

    static void restore() {
        ClientConfig.save(original);
    }

    static void enableAll() {
        var enabled = new HashSet<>(VanillaEntry.IDS);
        F3ather.ADDONS.entries().stream().limit(3).forEach(entry -> enabled.add(entry.id()));
        ClientConfig.save(new OverlaySettings(Mode.F3ATHER, "J", enabled, VanillaEntry.IDS, original.legacyAddons()));
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
        pressDebugChord(mc, GLFW.GLFW_KEY_J);
        requireSettings(mc);
        key(mc, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_PRESS);
        if (mc.screen != null || ClientConfig.current() != configured) {
            throw new IllegalStateException("Escape did not cancel the settings draft");
        }

        ClientConfig.save(new OverlaySettings(Mode.TRADITIONAL, "K", configured.enabled(), configured.order(),
                configured.legacyAddons()));
        pressDebugChord(mc, GLFW.GLFW_KEY_K);
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
