package top.hachimi2333.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.stb.STBTruetype.*;

public final class ModernFont extends SimplePreparableReloadListener<ByteBuffer> {
    public static final ModernFont INSTANCE = new ModernFont();

    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("f3ather", "font/noto-sans-sc.ttf");
    private static final int PAGE_SIZE = 1024;
    private static final int PAGE_LIMIT = 4;
    private static final int METRIC_LIMIT = 8192;
    private static final int REPLACEMENT = 0xFFFD;

    private final List<Page> pages = new ArrayList<>(PAGE_LIMIT);
    private final Map<Integer, Metric> metrics = new HashMap<>();
    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private ByteBuffer fontData;
    private STBTTFontinfo font;
    private int deviceScale;
    private float unitScale;
    private float rasterScale;
    private float ascent;

    private ModernFont() {}

    @Override
    protected ByteBuffer prepare(ResourceManager manager, ProfilerFiller profiler) {
        // The shipped face and its metrics stay consistent across resource packs.
        try (var stream = ModernFont.class.getResourceAsStream("/assets/" + FONT.getNamespace() + "/" + FONT.getPath())) {
            if (stream == null) throw new IOException(FONT.toString());
            return TextureUtil.readResource(stream).flip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void apply(ByteBuffer bytes, ResourceManager manager, ProfilerFiller profiler) {
        release();
        fontData = bytes;
        font = STBTTFontinfo.malloc();
        if (!stbtt_InitFont(font, fontData)) {
            release();
            throw new IllegalStateException("Could not initialize the F3ather font");
        }
        try (var stack = MemoryStack.stackPush()) {
            var asc = stack.mallocInt(1);
            stbtt_GetFontVMetrics(font, asc, null, null);
            unitScale = stbtt_ScaleForPixelHeight(font, 1);
            ascent = asc.get(0) * unitScale;
        }
    }

    public void release() {
        clearPages();
        metrics.clear();
        if (font != null) {
            font.free();
            font = null;
        }
        if (fontData != null) {
            MemoryUtil.memFree(fontData);
            fontData = null;
        }
        deviceScale = 0;
    }

    private void clearPages() {
        for (Page page : pages) {
            Minecraft.getInstance().getTextureManager().release(page.id);
        }
        pages.clear();
        glyphs.clear();
    }

    public float width(String text, float size) {
        if (font == null) return 0;
        float width = 0;
        for (int index = 0; index < text.length();) {
            int code = text.codePointAt(index);
            index += Character.charCount(code);
            if (code == '§' && index < text.length()) {
                index++;
                continue;
            }
            width += metric(code).advance() * size;
        }
        return width;
    }

    public String fit(String text, float size, float maximum) {
        if (width(text, size) <= maximum) return text;
        float remaining = maximum - width("…", size);
        if (remaining < 0) return "";
        int index = 0;
        while (index < text.length()) {
            int cp = text.codePointAt(index);
            int next = index + Character.charCount(cp);
            if (cp == '§' && next < text.length()) {
                index = next + 1;
                continue;
            }
            remaining -= metric(cp).advance() * size;
            if (remaining < 0) break;
            index = next;
        }
        return text.substring(0, index) + "…";
    }

    public void prepareText(String text) {
        if (font == null) return;
        int scale = Math.clamp((int) Math.ceil(Minecraft.getInstance().getWindow().getGuiScale()), 2, 4);
        if (scale != deviceScale) {
            clearPages();
            deviceScale = scale;
            rasterScale = stbtt_ScaleForPixelHeight(font, 13 * deviceScale);
        }
        glyphFor(REPLACEMENT);
        for (int index = 0; index < text.length();) {
            int code = text.codePointAt(index);
            index += Character.charCount(code);
            if (code == '§' && index < text.length()) {
                index++;
                continue;
            }
            glyphFor(code);
        }
    }

    public void upload() {
        for (Page page : pages) {
            if (page.dirty) {
                page.texture.upload();
                page.dirty = false;
            }
        }
    }

    public void draw(GuiGraphics graphics, String text, float x, float y, float size, int color) {
        prepareText(text);
        upload();
        drawPrepared(graphics, text, x, y, size, color);
    }

    public void drawPrepared(GuiGraphics graphics, String text, float x, float y, float size, int color) {
        if (font == null) return;
        float scale = size / (13 * deviceScale);
        Matrix4f pose = graphics.pose().last().pose();
        Page lastPage = null;
        VertexConsumer buffer = null;
        int currentColor = color;
        for (int index = 0; index < text.length();) {
            int code = text.codePointAt(index);
            index += Character.charCount(code);
            if (code == '§' && index < text.length()) {
                var format = net.minecraft.ChatFormatting.getByCode(text.charAt(index++));
                if (format == net.minecraft.ChatFormatting.RESET) currentColor = color;
                else if (format != null && format.isColor()) currentColor = (color & 0xff000000) | format.getColor();
                continue;
            }
            Metric metric = metric(code);
            Glyph glyph = glyphs.get(metric.index());
            if (glyph == null) glyph = glyphs.get(metric(REPLACEMENT).index());
            if (glyph != null && glyph.page() != null) {
                if (glyph.page() != lastPage) {
                    lastPage = glyph.page();
                    buffer = graphics.bufferSource().getBuffer(lastPage.type);
                }
                float left = x + glyph.xOffset() * scale;
                float top = y + size * ascent + glyph.yOffset() * scale;
                float right = left + glyph.width() * scale;
                float bottom = top + glyph.height() * scale;
                float u = glyph.x() / (float) PAGE_SIZE;
                float v = glyph.y() / (float) PAGE_SIZE;
                float u2 = (glyph.x() + glyph.width()) / (float) PAGE_SIZE;
                float v2 = (glyph.y() + glyph.height()) / (float) PAGE_SIZE;
                vertex(buffer, pose, left, top, u, v, currentColor);
                vertex(buffer, pose, left, bottom, u, v2, currentColor);
                vertex(buffer, pose, right, bottom, u2, v2, currentColor);
                vertex(buffer, pose, right, top, u2, v, currentColor);
            }
            x += metric.advance() * size;
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float u, float v, int color) {
        buffer.addVertex(pose, x, y, 1).setColor(color).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    private Metric metric(int code) {
        Metric known = metrics.get(code);
        if (known != null) return known;
        int index = stbtt_FindGlyphIndex(font, code);
        if (index == 0 && code != REPLACEMENT) return metric(REPLACEMENT);
        try (var stack = MemoryStack.stackPush()) {
            var advance = stack.mallocInt(1);
            stbtt_GetGlyphHMetrics(font, index, advance, null);
            Metric metric = new Metric(index, advance.get(0) * unitScale);
            if (metrics.size() < METRIC_LIMIT) metrics.put(code, metric);
            return metric;
        }
    }

    private void glyphFor(int code) {
        int index = metric(code).index();
        if (glyphs.containsKey(index) || glyphs.size() >= METRIC_LIMIT) return;
        try (var stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            var x = stack.mallocInt(1);
            var y = stack.mallocInt(1);
            ByteBuffer bitmap = stbtt_GetGlyphBitmap(font, rasterScale, rasterScale, index, width, height, x, y);
            Glyph glyph = new Glyph(null, 0, 0, 0, 0, 0, 0);
            if (bitmap != null) {
                try {
                    int w = width.get(0);
                    int h = height.get(0);
                    Page page = pageFor(w, h);
                    if (page != null) glyph = page.add(bitmap, w, h, x.get(0), y.get(0));
                } finally {
                    stbtt_FreeBitmap(bitmap);
                }
                if (glyph.page() == null) {
                    glyph = glyphs.getOrDefault(metric(REPLACEMENT).index(), glyph);
                }
            }
            glyphs.put(index, glyph);
        }
    }

    private Page pageFor(int w, int h) {
        for (Page page : pages) {
            if (page.place(w, h)) return page;
        }
        if (w + 2 >= PAGE_SIZE || h + 2 >= PAGE_SIZE || pages.size() == PAGE_LIMIT) return null;
        var page = new Page(pages.size());
        pages.add(page);
        return page;
    }

    private record Metric(int index, float advance) {}
    private record Glyph(Page page, int x, int y, int width, int height, int xOffset, int yOffset) {}

    private static final class Page {
        private final ResourceLocation id;
        private final DynamicTexture texture;
        private final RenderType type;
        private int x = 1;
        private int y = 1;
        private int rowHeight;
        private boolean dirty;

        private Page(int page) {
            id = ResourceLocation.fromNamespaceAndPath("f3ather", "font/page_" + page);
            texture = new DynamicTexture(PAGE_SIZE, PAGE_SIZE, true);
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            type = RenderType.text(id);
        }

        private boolean place(int width, int height) {
            if (x + width + 1 >= PAGE_SIZE) {
                x = 1;
                y += rowHeight;
                rowHeight = 0;
            }
            return x + width + 1 < PAGE_SIZE && y + height + 1 < PAGE_SIZE;
        }

        private Glyph add(ByteBuffer bitmap, int width, int height, int offsetX, int offsetY) {
            NativeImage pixels = texture.getPixels();
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int alpha = bitmap.get(row * width + col) & 255;
                    pixels.setPixelRGBA(x + col, y + row, (alpha << 24) | 0xffffff);
                }
            }
            Glyph glyph = new Glyph(this, x, y, width, height, offsetX, offsetY);
            x += width + 2;
            rowHeight = Math.max(rowHeight, height + 2);
            dirty = true;
            return glyph;
        }
    }
}
