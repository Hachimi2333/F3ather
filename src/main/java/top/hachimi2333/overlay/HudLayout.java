package top.hachimi2333.overlay;

import top.hachimi2333.overlay.OverlaySettings.Region;

import java.util.List;
import java.util.Map;

public final class HudLayout {
    public static final int ROW_HEIGHT = 9;
    public static final int HEADER = 18;
    private static final int EDGE = 2;

    private HudLayout() {}

    public static Rect region(int width, int top, int bottom, Region region, boolean split) {
        int middle = top + (bottom - top) / 2;
        return new Rect(region.left() ? 0 : width / 2, split && !region.top() ? middle : top,
                region.left() ? width / 2 : width, split && region.top() ? middle : bottom);
    }

    public static Rect bounds(int width, int top, int bottom, Region region, Map<Region, List<String>> rows, boolean fourCorners) {
        Region other = switch (region) {
            case TOP_LEFT -> Region.BOTTOM_LEFT;
            case BOTTOM_LEFT -> Region.TOP_LEFT;
            case TOP_RIGHT -> Region.BOTTOM_RIGHT;
            case BOTTOM_RIGHT -> Region.TOP_RIGHT;
        };
        return region(width, top, bottom, region, fourCorners || !rows.getOrDefault(other, List.of()).isEmpty());
    }

    public static int pageCount(int width, int top, int leftBottom, int rightBottom, Map<Region, List<String>> rows, boolean fourCorners) {
        int pages = 1;
        for (Region region : Region.values()) {
            int count = rows.getOrDefault(region, List.of()).size();
            int capacity = bounds(width, top, region.left() ? leftBottom : rightBottom, region, rows, fourCorners).capacity();
            if (capacity > 0) pages = Math.max(pages, Math.ceilDiv(count, capacity));
        }
        return pages;
    }

    public static List<String> page(List<String> rows, Rect area, int page) {
        int capacity = area.capacity();
        if (capacity == 0 || page >= Math.ceilDiv(rows.size(), capacity)) return List.of();
        int start = page * capacity;
        return rows.subList(start, start + Math.min(capacity, rows.size() - start));
    }

    public record Position(int columnDistance, int headerHeight, int layoutHeader) {
        static final Position EXPANDED = new Position(0, HEADER, HEADER);
        private static final Position HEADERLESS = new Position(0, 0, 0);

        public static Position preview(boolean headerHidden) { return headerHidden ? HEADERLESS : EXPANDED; }

        Position withHeaderHidden(boolean hidden) {
            return hidden ? new Position(columnDistance, 0, 0) : this;
        }

        public int columnX(boolean left) { return left ? -columnDistance : columnDistance; }
        public int headerY() { return headerHeight - HEADER; }
    }

    public record Rect(int left, int top, int right, int bottom) {
        public int capacity() { return Math.max(0, (bottom - top - EDGE * 2) / ROW_HEIGHT); }
        public int firstY(boolean topAligned, int lineCount) {
            return topAligned ? top + EDGE : bottom - EDGE - lineCount * ROW_HEIGHT;
        }
    }
}
