package top.hachimi2333.overlay;

final class OverlayMotion {
    private static final long OPEN_DURATION = 200_000_000L;
    private static final long HEADER_DURATION = 160_000_000L;
    private static final long IDLE_DURATION = 4_000_000_000L;

    private boolean open;
    private boolean openingColumns;
    private long openedAt;
    private long lastActivity;
    private boolean showHeader;
    private long headerStartedAt;
    private long headerDuration;
    private double headerFrom;
    private int layoutHeader;

    void reset() {
        open = false;
    }

    void reveal(long now, boolean disabled) {
        update(now, disabled);
        lastActivity = now;
        if (!showHeader) transition(true, now, disabled);
    }

    HudLayout.Position position(long now, boolean disabled, int width) {
        update(now, disabled);
        double remaining = openingColumns ? 1 - fraction(now, openedAt, OPEN_DURATION) : 0;
        int columnDistance = (int) Math.round(width * remaining * remaining * remaining);
        int headerHeight = (int) Math.round(HudLayout.HEADER * headerProgress(now));
        return new HudLayout.Position(columnDistance, headerHeight, layoutHeader);
    }

    private void update(long now, boolean disabled) {
        if (!open) {
            open = true;
            openingColumns = !disabled;
            openedAt = now;
            lastActivity = now;
            showHeader = true;
            headerStartedAt = now;
            headerDuration = disabled ? 0 : OPEN_DURATION;
            headerFrom = 0;
            layoutHeader = HudLayout.HEADER;
        }
        if (showHeader && now - lastActivity >= IDLE_DURATION) {
            transition(false, lastActivity + IDLE_DURATION, disabled);
        }
        if (disabled || now - openedAt >= OPEN_DURATION) openingColumns = false;
        if (disabled) headerDuration = 0;
        if (now - headerStartedAt >= headerDuration) layoutHeader = showHeader ? HudLayout.HEADER : 0;
    }

    private void transition(boolean visible, long now, boolean disabled) {
        headerFrom = headerProgress(now);
        headerStartedAt = now;
        headerDuration = disabled ? 0 : HEADER_DURATION;
        showHeader = visible;
    }

    private double headerProgress(long now) {
        double target = showHeader ? 1 : 0;
        if (headerDuration == 0) return target;
        double progress = fraction(now, headerStartedAt, headerDuration);
        double remaining = 1 - progress;
        double eased = headerDuration == OPEN_DURATION ? 1 - remaining * remaining * remaining
                : progress * progress * (3 - 2 * progress);
        return headerFrom + (target - headerFrom) * eased;
    }

    private static double fraction(long now, long start, long duration) {
        return Math.clamp((double) (now - start) / duration, 0, 1);
    }
}
