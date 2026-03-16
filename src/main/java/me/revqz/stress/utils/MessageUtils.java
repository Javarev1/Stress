package me.revqz.stress.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public final class MessageUtils {

    public static final TextColor ACCENT = TextColor.color(0x10B981); // Green accent
    public static final TextColor ERROR = TextColor.color(0xEF4444);  // Red
    public static final TextColor TEXT = TextColor.color(0xD1D5DB);   // Light Gray
    public static final TextColor DARK = TextColor.color(0x374151);   // Dark Gray (separator)

    private MessageUtils() {}

    public static Component prefix() {
        return Component.text("Stress ", ACCENT)
                .append(Component.text("» ", DARK));
    }

    public static Component error(String msg) {
        return prefix().append(Component.text(msg, ERROR));
    }

    public static Component success(String msg) {
        return prefix().append(Component.text(msg, ACCENT));
    }

    public static Component info(String msg) {
        return prefix().append(Component.text(msg, TEXT));
    }
    
    public static Component format(String msg, TextColor color) {
        return Component.text(msg, color);
    }
}
