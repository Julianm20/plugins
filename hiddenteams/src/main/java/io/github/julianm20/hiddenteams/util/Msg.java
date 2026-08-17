package io.github.julianm20.hiddenteams.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Message formatting helpers.
 *
 * <p>Config strings use MiniMessage tags ({@code <gold>}, {@code <bold>}, ...) and
 * {@code %placeholder%} substitutions. Substitution happens before parsing, so team
 * names must be validated at creation time (they are) and player-typed chat is never
 * substituted as a string - it is spliced in as a component instead.
 */
public final class Msg {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Msg() {
    }

    public static String fill(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return "";
        }
        String out = raw;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return out;
    }

    public static Component parse(String raw) {
        return parse(raw, null);
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        String filled = fill(raw, placeholders);
        try {
            return MINI.deserialize(filled);
        } catch (RuntimeException ex) {
            return Component.text(filled);
        }
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static Map<String, String> map(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("placeholders must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }
}
