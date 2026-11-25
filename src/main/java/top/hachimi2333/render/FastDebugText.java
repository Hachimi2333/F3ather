package top.hachimi2333.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public final class FastDebugText {
    private static final int MAX_LENGTH = 192;
    private static final int MAX_VERTICES = 512;
    private static final int STRIDE = 5;
    private final RunCache cache = new RunCache();
    private Font currentFont;
    private String lastText;
    private int lastColor;
    private Entry lastEntry;

    public void clear() {
        cache.clear();
        currentFont = null;
        lastText = null;
        lastEntry = null;
    }

    public int width(Font font, String text, int color) {
        Entry entry = entry(font, text, color);
        return entry != null ? entry.width : font.width(text);
    }

    public int draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        Entry entry = entry(font, text, color);
        if (entry == null) return graphics.drawString(font, text, x, y, color, false);
        if (!entry.drawn || entry.useVanilla) {
            entry.drawn = true;
            return graphics.drawString(font, text, x, y, color, false);
        }
        if (entry.run == null) {
            var recorder = new Recorder();
            entry.advance = font.drawInBatch(text, 0, 0, color, false, new Matrix4f(), recorder::buffer,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            if (recorder.size > MAX_VERTICES) {
                entry.useVanilla = true;
                return graphics.drawString(font, text, x, y, color, false);
            }
            entry.run = recorder.finish();
        }
        entry.run.draw(graphics, x, y);
        return x + entry.advance;
    }

    private boolean cacheable(Font font, String text) {
        return text.length() <= MAX_LENGTH && !font.isBidirectional()
                && text.indexOf("§k") < 0 && text.indexOf("§K") < 0;
    }

    private Entry entry(Font font, String text, int color) {
        if (currentFont != font) {
            clear();
            currentFont = font;
        }
        // renderLines measures a row immediately before drawing that same String.
        if (lastText == text && lastColor == color) return lastEntry;
        if (!cacheable(font, text)) return null;
        lastEntry = cache.entry(text, color, () -> font.width(text));
        lastText = text;
        lastColor = color;
        return lastEntry;
    }

    static final class RunCache extends LinkedHashMap<Key, Entry> {
        static final int LIMIT = 512;

        RunCache() { super(128, 0.75f, true); }

        Entry entry(String text, int color, IntSupplier measure) {
            var key = new Key(text, color);
            var cached = get(key);
            if (cached != null) return cached;
            var entry = new Entry(measure.getAsInt());
            put(key, entry);
            return entry;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, FastDebugText.Entry> eldest) { return size() > LIMIT; }
    }

    private record Key(String text, int color) {}

    static final class Entry {
        final int width;
        boolean drawn;
        boolean useVanilla;
        int advance;
        Run run;

        Entry(int width) { this.width = width; }
    }

    private record Span(RenderType type, int start) {}

    private record Run(float[] geometry, int[] colors, List<Span> spans, float minY, float maxY) {
        void draw(GuiGraphics graphics, int x, int y) {
            Matrix4f pose = graphics.pose().last().pose();
            boolean translation = (pose.properties() & Matrix4fc.PROPERTY_TRANSLATION) != 0;
            if (translation) {
                float originY = y + pose.m31();
                if (originY + minY >= graphics.guiHeight() || originY + maxY < 0) return;
            }
            var buffers = graphics.bufferSource();
            for (int i = 0; i < spans.size(); i++) {
                Span span = spans.get(i);
                int end = i + 1 < spans.size() ? spans.get(i + 1).start() : colors.length;
                VertexConsumer output = buffers.getBuffer(span.type());
                for (int vertex = span.start(); vertex < end; vertex++) {
                    int at = vertex * STRIDE;
                    float px = x + geometry[at];
                    float py = y + geometry[at + 1];
                    float pz = geometry[at + 2];
                    if (translation) output.addVertex(px + pose.m30(), py + pose.m31(), pz + pose.m32());
                    else output.addVertex(pose, px, py, pz);
                    output.setColor(colors[vertex]).setUv(geometry[at + 3], geometry[at + 4])
                            .setLight(LightTexture.FULL_BRIGHT);
                }
            }
        }
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Span> spans = new ArrayList<>(2);
        private float[] geometry = new float[64 * STRIDE];
        private int[] colors = new int[64];
        private int size;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        VertexConsumer buffer(RenderType type) {
            if (spans.isEmpty() || spans.getLast().type() != type) spans.add(new Span(type, size));
            return this;
        }

        Run finish() {
            return new Run(Arrays.copyOf(geometry, size * STRIDE), Arrays.copyOf(colors, size), List.copyOf(spans), minY, maxY);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (size == colors.length) {
                colors = Arrays.copyOf(colors, size * 2);
                geometry = Arrays.copyOf(geometry, size * 2 * STRIDE);
            }
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            int at = size++ * STRIDE;
            geometry[at] = x;
            geometry[at + 1] = y;
            geometry[at + 2] = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            colors[size - 1] = a << 24 | r << 16 | g << 8 | b;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            int at = (size - 1) * STRIDE;
            geometry[at + 3] = u;
            geometry[at + 4] = v;
            return this;
        }

        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
