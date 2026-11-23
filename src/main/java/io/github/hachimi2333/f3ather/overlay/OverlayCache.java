package io.github.hachimi2333.f3ather.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class OverlayCache {
    static final long INTERVAL_NANOS = 100_000_000L;

    private Context context;
    private Snapshot leftSnapshot;
    private Snapshot rightSnapshot;

    public void invalidate() {
        context = null;
        leftSnapshot = null;
        rightSnapshot = null;
    }

    public Frame begin(long now, Context next) {
        if (!next.equals(context)) {
            invalidate();
            context = next;
        }
        return new Frame(now, next.settings());
    }

    public record Context(Object level, Object camera, boolean reduced, int charts, OverlaySettings settings) {}

    public final class Frame {
        private final long now;
        private final OverlaySettings settings;
        private Column leftColumn;
        private Column rightColumn;

        private Frame(long now, OverlaySettings settings) {
            this.now = now;
            this.settings = settings;
        }

        public List<String> collect(boolean left, Supplier<List<String>> collector, Supplier<String> demoLine) {
            Snapshot cached = left ? leftSnapshot : rightSnapshot;
            if (settings.refresh() == OverlaySettings.Refresh.LIMITED && cached != null
                    && now - cached.collectedAt() < INTERVAL_NANOS) {
                List<String> eventLines = new ArrayList<>(cached.lines());
                remember(left, eventLines, cached.vanilla());
                return eventLines;
            }

            List<String> eventLines = collector.get();
            Map<String, VanillaEntry> vanilla = OverlayText.identifyVanilla(eventLines, left, demoLine.get());
            remember(left, eventLines, vanilla);
            if (settings.refresh() == OverlaySettings.Refresh.LIMITED) {
                // The event owns the original list. Its changes must never enter the next frame's snapshot.
                Snapshot fresh = new Snapshot(now, new ArrayList<>(eventLines), vanilla);
                if (left) {
                    leftSnapshot = fresh;
                } else {
                    rightSnapshot = fresh;
                }
            }
            return eventLines;
        }

        public List<String> forDrawing(List<String> lines, boolean left, AddonEntries addons) {
            Column column = left ? leftColumn : rightColumn;
            if (column == null || lines != column.eventLines()) {
                return lines;
            }
            return OverlayText.filter(lines, column.vanilla(), left, settings, addons);
        }

        private void remember(boolean left, List<String> lines, Map<String, VanillaEntry> vanilla) {
            Column column = new Column(lines, vanilla);
            if (left) {
                leftColumn = column;
            } else {
                rightColumn = column;
            }
        }
    }

    private record Column(List<String> eventLines, Map<String, VanillaEntry> vanilla) {}
    private record Snapshot(long collectedAt, List<String> lines, Map<String, VanillaEntry> vanilla) {}
}
