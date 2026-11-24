package top.hachimi2333.config;

import com.electronwill.nightconfig.toml.TomlFormat;
import org.junit.jupiter.api.Test;
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
        var format = TomlFormat.instance();
        var restored = format.createParser().parse(format.createWriter().writeToString(config));

        assertTrue(ClientConfig.SPEC.isCorrect(restored));
        List<String> encoded = restored.get("customTabs");
        assertEquals(tab, ClientConfig.decodeTab(encoded.getFirst()));
        assertEquals(false, restored.get("builtIn.simple"));
        assertEquals(0, restored.<Integer>get("refreshHz"));
        assertEquals(true, restored.get("compatibility"));
    }

    @Test
    void incompleteOrInvalidTabsAreSkipped() {
        assertNull(ClientConfig.decodeTab("{}"));
        assertNull(ClientConfig.decodeTab("invalid"));
        assertNull(ClientConfig.decodeTab("{\"id\":\"" + Tab.create("Test").id() + "\",\"name\":{}}"));
        assertNull(ClientConfig.decodeTab("{\"id\":\"simple\",\"name\":\"Foo\"}"));
    }
}
