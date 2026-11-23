package io.github.hachimi2333.f3ather.overlay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AddonEntries {
    public static final int LIMIT = 512;
    private static final int MAX_LABEL_LENGTH = 80;
    private static final String LEFT = "addon:left:";
    private static final String RIGHT = "addon:right:";

    private final Map<String, String> labels = new LinkedHashMap<>();

    public static String otherId(boolean left) {
        return left ? LEFT : RIGHT;
    }

    public static boolean isAddonId(String id) {
        return id.startsWith(LEFT) || id.startsWith(RIGHT);
    }

    public static boolean isLeft(String id) {
        return id.startsWith(LEFT);
    }

    public static String labelFromId(String id) {
        int offset = isLeft(id) ? LEFT.length() : RIGHT.length();
        return id.substring(offset);
    }

    public Map<String, String> labels() {
        return Collections.unmodifiableMap(labels);
    }

    String identify(String line, boolean left, OverlaySettings settings) {
        String prefix = otherId(left);
        String label = findLabel(line);
        if (label == null) {
            return prefix;
        }
        String id = prefix + label;
        if (labels.containsKey(id) || settings.overrides().containsKey(id)) {
            return id;
        }
        if (labels.size() >= LIMIT) {
            return prefix;
        }
        labels.put(id, label);
        return id;
    }

    private static String findLabel(String line) {
        int end = Math.min(line.length(), MAX_LABEL_LENGTH + 1);
        for (int i = 0; i < end; i++) {
            char ch = line.charAt(i);
            if (ch == '：' || ((ch == ':' || ch == '=') && i + 1 < line.length()
                    && Character.isWhitespace(line.charAt(i + 1)))) {
                String label = stripFormatting(line.substring(0, i)).strip();
                if (label.isEmpty() || !Character.isLetter(label.codePointAt(0))) {
                    return null;
                }
                return label;
            }
        }
        return null;
    }

    static String stripFormatting(String line) {
        int firstCode = line.indexOf('§');
        if (firstCode < 0) {
            return line;
        }
        StringBuilder plain = new StringBuilder(line.length()).append(line, 0, firstCode);
        for (int i = firstCode; i < line.length(); i++) {
            if (line.charAt(i) == '§' && i + 1 < line.length()) {
                i++;
            } else {
                plain.append(line.charAt(i));
            }
        }
        return plain.toString();
    }
}
