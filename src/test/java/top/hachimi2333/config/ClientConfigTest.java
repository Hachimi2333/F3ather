package top.hachimi2333.config;

import com.electronwill.nightconfig.toml.TomlFormat;
import org.junit.jupiter.api.Test;
import top.hachimi2333.overlay.OverlaySettings;
import top.hachimi2333.overlay.OverlaySettings.BuiltIn;
import top.hachimi2333.overlay.OverlaySettings.Region;
import top.hachimi2333.overlay.OverlaySettings.Tab;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ClientConfigTest {
    @Test
    void customTabRoundTripsWithUnicodeAndOrderedEntries() {
        var tab = Tab.create("红石 / Redstone").toggle(false)
                .move("vanilla:facing", Region.TOP_LEFT, 0)
                .move("vanilla:xyz", Region.TOP_LEFT, 1)
                .move("addon:right:example:Status:1", Region.BOTTOM_RIGHT, 0);
        var config = TomlFormat.newConfig();
        ClientConfig.SPEC.correct(config);
        config.set("customTabs", List.of(ClientConfig.encodeTab(tab)));
        config.set("builtIn.simple", false);
        config.set("refreshHz", 0);
        config.set("compatibility", true);
        config.set("disableAnimations", true);
        config.set("lockedTab", "performance");
        var format = TomlFormat.instance();
        var restored = format.createParser().parse(format.createWriter().writeToString(config));

        assertTrue(ClientConfig.SPEC.isCorrect(restored));
        List<String> encoded = restored.get("customTabs");
        assertEquals(tab, ClientConfig.decodeTab(encoded.getFirst()));
        assertEquals(false, restored.get("builtIn.simple"));
        assertEquals(0, restored.<Integer>get("refreshHz"));
        assertEquals(true, restored.get("compatibility"));
        assertEquals(true, restored.get("disableAnimations"));
        assertEquals("performance", restored.get("lockedTab"));
    }

    @Test
    void invalidRefreshRatesResetWithoutChangingOtherSettings() {
        var config = TomlFormat.newConfig();
        ClientConfig.SPEC.correct(config);
        config.remove("disableAnimations");
        config.remove("lockedTab");
        config.set("refreshHz", 30);
        config.set("builtIn.world", false);
        ClientConfig.SPEC.correct(config);

        assertEquals(false, config.get("disableAnimations"));
        assertEquals("", config.get("lockedTab"));
        assertEquals("", OverlaySettings.DEFAULT.lockedTab());
        assertEquals(10, config.<Integer>get("refreshHz"));
        assertEquals(false, config.get("builtIn.world"));
        assertTrue(OverlaySettings.DEFAULT.withDisableAnimations(true).withRefresh(20)
                .withBuiltIn(BuiltIn.WORLD, false).withCompatibility(true).disableAnimations());
        var lockedWorld = OverlaySettings.DEFAULT.withBuiltIn(BuiltIn.WORLD, false).withLockedTab("world");
        assertEquals("world", lockedWorld.lockedTab());
        assertTrue(lockedWorld.enabled().contains(BuiltIn.WORLD));
        assertEquals("", lockedWorld.withBuiltIn(BuiltIn.WORLD, false).lockedTab());

        var custom = Tab.create("Custom").toggle(false);
        var lockedCustom = OverlaySettings.DEFAULT.withTab(custom).withLockedTab(custom.id());
        assertEquals(custom.id(), lockedCustom.lockedTab());
        assertTrue(lockedCustom.customTab(custom.id()).enabled());
        assertEquals("", lockedCustom.withTab(custom).lockedTab());
        assertEquals("", lockedCustom.withoutTab(custom.id()).lockedTab());
    }

    @Test
    void onlyTheFivePresetsAreAccepted() {
        var config = TomlFormat.newConfig();
        ClientConfig.SPEC.correct(config);
        assertEquals(10, config.<Integer>get("refreshHz"));
        assertEquals(List.of(1, 5, 10, 20, 0), OverlaySettings.REFRESH_PRESETS);
        for (int preset : OverlaySettings.REFRESH_PRESETS) {
            config.set("refreshHz", preset);
            assertTrue(ClientConfig.SPEC.isCorrect(config));
            assertEquals(preset, OverlaySettings.DEFAULT.withRefresh(preset).refreshHz());
        }
        assertEquals(10, OverlaySettings.DEFAULT.withRefresh(16).refreshHz());
    }

    @Test
    void incompleteOrInvalidTabsAreSkipped() {
        String valid = ClientConfig.encodeTab(Tab.create("Test"));
        assertNull(ClientConfig.decodeTab(valid.replace("\"top_left\":[]", "\"top_left\":\"vanilla:fps\"")));
        assertNull(ClientConfig.decodeTab(valid.replace("\"regions\":{", "\"regions\":false,\"unused\":{")));
        assertNull(ClientConfig.decodeTab("{}"));
        assertNull(ClientConfig.decodeTab("invalid"));
        assertNull(ClientConfig.decodeTab("{\"id\":\"" + Tab.create("Test").id() + "\",\"name\":{}}"));
        assertNull(ClientConfig.decodeTab("{\"id\":\"simple\",\"name\":\"Foo\"}"));
    }
}
