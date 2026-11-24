package top.hachimi2333.overlay;

final class OverlayMotion {
    private static final long OVERLAY_DURATION = 200_000_000L;
    private static final long HEADER_DURATION = 160_000_000L;
    private static final long IDLE_DURATION = 4_000_000_000L;

    private Phase phase = Phase.HIDDEN;
    private long columnsStartedAt;
    private double columnsFrom;
    private long lastActivity;
    private boolean showHeader;
    private long headerStartedAt;
    private long headerDuration;
    private double headerFrom;
    private int layoutHeader;

    void reset() {
        phase = Phase.HIDDEN;
    }

    void open(long now, boolean disabled) {
        if (phase == Phase.OPEN) return;
        boolean reversing = phase == Phase.CLOSING && now - columnsStartedAt < OVERLAY_DURATION;
        columnsFrom = disabled ? 0 : reversing ? columnProgress(now) : 1;
        headerFrom = reversing ? headerProgress(now) : 0;
        if (!reversing || disabled) layoutHeader = HudLayout.HEADER;
        phase = Phase.OPEN;
        columnsStartedAt = now;
        lastActivity = now;
        showHeader = true;
        headerStartedAt = now;
        headerDuration = disabled ? 0 : OVERLAY_DURATION;
    }

    void close(long now, boolean disabled) {
        if (disabled || phase != Phase.OPEN) {
            reset();
            return;
        }
        updateOpen(now, false);
        columnsFrom = columnProgress(now);
        columnsStartedAt = now;
        transition(false, now, OVERLAY_DURATION);
        phase = Phase.CLOSING;
    }

    void reveal(long now, boolean disabled) {
        open(now, disabled);
        updateOpen(now, disabled);
        lastActivity = now;
        if (!showHeader) transition(true, now, disabled ? 0 : HEADER_DURATION);
    }

    HudLayout.Position position(long now, boolean disabled, int width) {
        open(now, disabled);
        updateOpen(now, disabled);
        return at(now, width);
    }

    HudLayout.Position exitPosition(long now, boolean disabled, int width) {
        if (phase != Phase.CLOSING) return null;
        if (disabled || now - columnsStartedAt >= OVERLAY_DURATION) {
            reset();
            return null;
        }
        return at(now, width);
    }

    private HudLayout.Position at(long now, int width) {
        int columnDistance = (int) Math.round(width * columnProgress(now));
        int headerHeight = (int) Math.round(HudLayout.HEADER * headerProgress(now));
        return new HudLayout.Position(columnDistance, headerHeight, layoutHeader);
    }

    private void updateOpen(long now, boolean disabled) {
        if (showHeader && now - lastActivity >= IDLE_DURATION) {
            transition(false, lastActivity + IDLE_DURATION, disabled ? 0 : HEADER_DURATION);
        }
        if (disabled) {
            columnsFrom = 0;
            headerDuration = 0;
        }
        if (now - headerStartedAt >= headerDuration) layoutHeader = showHeader ? HudLayout.HEADER : 0;
    }

    private void transition(boolean visible, long now, long duration) {
        headerFrom = headerProgress(now);
        headerStartedAt = now;
        headerDuration = duration;
        showHeader = visible;
    }

    private double columnProgress(long now) {
        double progress = fraction(now, columnsStartedAt, OVERLAY_DURATION);
        if (phase == Phase.CLOSING) return columnsFrom + (1 - columnsFrom) * cube(progress);
        return columnsFrom * cube(1 - progress);
    }

    private double headerProgress(long now) {
        double target = showHeader ? 1 : 0;
        if (headerDuration == 0) return target;
        double progress = fraction(now, headerStartedAt, headerDuration);
        double eased = headerDuration == OVERLAY_DURATION ? (showHeader ? 1 - cube(1 - progress) : cube(progress))
                : progress * progress * (3 - 2 * progress);
        return headerFrom + (target - headerFrom) * eased;
    }

    private static double fraction(long now, long start, long duration) {
        return Math.clamp((double) (now - start) / duration, 0, 1);
    }

    private static double cube(double value) { return value * value * value; }

    private enum Phase { HIDDEN, OPEN, CLOSING }
}
