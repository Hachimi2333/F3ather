package top.hachimi2333.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FastDebugTextTest {
    @Test
    void repeatedLinesReuseWidthAndDynamicLinesCannotGrowTheCache() {
        var cache = new FastDebugText.RunCache();
        var original = cache.entry("stable", 0xe0e0e0, () -> 34);
        for (int i = 0; i < FastDebugText.RunCache.LIMIT * 2; i++) {
            cache.entry("dynamic " + i, 0xe0e0e0, () -> 45);
            assertSame(original, cache.entry("stable", 0xe0e0e0, () -> fail("Repeated line was measured again")));
        }
        assertEquals(34, original.width);
        assertEquals(FastDebugText.RunCache.LIMIT, cache.size());
        cache.clear();
        assertNotSame(original, cache.entry("stable", 0xe0e0e0, () -> 32));
    }
}
