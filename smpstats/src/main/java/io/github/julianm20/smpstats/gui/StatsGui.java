package io.github.julianm20.smpstats.gui;

import io.github.julianm20.smpstats.PlayerStats;
import io.github.julianm20.smpstats.SMPStatsPlugin;
import io.github.julianm20.smpstats.api.StatCategory;
import io.github.julianm20.smpstats.api.StatDefinition;
import io.github.julianm20.smpstats.util.Fmt;
import io.github.julianm20.smpstats.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds and opens the read-only profile window. */
public final class StatsGui {

    /** Slot the profile head sits in, centred on the top row. */
    private static final int HEAD_SLOT = 4;

    private final SMPStatsPlugin plugin;

    public StatsGui(SMPStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, PlayerStats stats) {
        int rows = plugin.settings().guiRows();
        int size = rows * 9;

        StatsGuiHolder holder = new StatsGuiHolder(stats.uuid());
        Inventory inventory = Bukkit.createInventory(holder, size,
                Msg.parse(plugin.settings().guiTitle(), Msg.map("player", stats.name())));
        holder.inventory(inventory);

        fillBorder(inventory, size);
        inventory.setItem(HEAD_SLOT, head(stats));

        // Stats flow through the inner grid: every slot that is not on an edge column.
        List<Integer> slots = innerSlots(rows);
        int index = 0;
        boolean includeZero = plugin.settings().showEmptyStats();

        for (StatCategory category : StatCategory.values()) {
            Map<StatDefinition, Long> entries = plugin.stats().profile(stats, category, includeZero);
            for (Map.Entry<StatDefinition, Long> entry : entries.entrySet()) {
                if (index >= slots.size()) {
                    // More statistics than the window has room for; raise gui.rows to fit.
                    break;
                }
                inventory.setItem(slots.get(index++), statItem(entry.getKey(), entry.getValue(), category));
            }
        }

        viewer.openInventory(inventory);
    }

    private void fillBorder(Inventory inventory, int size) {
        Material filler = material(plugin.settings().guiFiller(), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack pane = new ItemStack(filler);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            // A blank name stops the window showing "Gray Stained Glass Pane" on hover.
            meta.displayName(Component.text(" "));
            pane.setItemMeta(meta);
        }
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, pane);
        }
    }

    /** Slots in columns 1-7 of every row except the first and last. */
    private List<Integer> innerSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row < rows - 1; row++) {
            for (int column = 1; column <= 7; column++) {
                slots.add(row * 9 + column);
            }
        }
        return slots;
    }

    private ItemStack head(PlayerStats stats) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            // By UUID, so this never triggers a blocking name lookup.
            OfflinePlayer owner = Bukkit.getOfflinePlayer(stats.uuid());
            skull.setOwningPlayer(owner);
        }
        if (meta != null) {
            meta.displayName(Msg.parse(plugin.settings().guiHeadName(),
                            Msg.map("player", stats.name()))
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : plugin.settings().guiHeadLore()) {
                lore.add(Msg.parse(line, Msg.map(
                                "player", stats.name(),
                                "kd", Fmt.ratio(stats.killDeathRatio()),
                                "weapon", weaponName(stats)))
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String weaponName(PlayerStats stats) {
        String weapon = stats.favouriteWeapon();
        return weapon == null ? plugin.settings().guiNoWeapon() : Fmt.prettyMaterial(weapon);
    }

    private ItemStack statItem(StatDefinition definition, long value, StatCategory category) {
        Material material = material(plugin.settings().guiIcon(definition.key()),
                material(plugin.settings().guiDefaultIcon(), Material.PAPER));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(plugin.settings().guiStatName(), Msg.map(
                            "icon", definition.icon(),
                            "name", definition.displayName()))
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.parse(plugin.settings().guiStatValue(), Msg.map(
                            "value", Fmt.value(value, definition.format())))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Msg.parse(plugin.settings().guiStatCategory(), Msg.map(
                            "category", category.displayName()))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Resolves a configured material name, warning once and falling back if it is unknown. */
    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material resolved = Material.matchMaterial(name);
        if (resolved == null || !resolved.isItem()) {
            plugin.getLogger().warning("Unknown GUI item '" + name + "', using " + fallback + " instead.");
            return fallback;
        }
        return resolved;
    }
}
