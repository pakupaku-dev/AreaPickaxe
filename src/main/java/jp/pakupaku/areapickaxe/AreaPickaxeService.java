package jp.pakupaku.areapickaxe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class AreaPickaxeService {
    private final AreaPickaxePlugin plugin;
    private boolean sneakForSingleBlock;
    private boolean requireCorrectTool;
    private boolean shiftVerticalAreaAboveFloor;
    private boolean protectBlockUnderPlayer;

    public AreaPickaxeService(AreaPickaxePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        sneakForSingleBlock = plugin.getConfig()
                .getBoolean("mining.sneak-for-single-block", true);
        requireCorrectTool = plugin.getConfig()
                .getBoolean("mining.require-correct-tool", true);
        shiftVerticalAreaAboveFloor = plugin.getConfig()
                .getBoolean("mining.shift-vertical-area-above-floor", true);
        protectBlockUnderPlayer = plugin.getConfig()
                .getBoolean("mining.protect-block-under-player", true);
    }

    public ItemStack createPickaxe(Material material, int size) {
        requireAllowedMaterial(material);
        requireValidSize(size);
        ItemStack item = new ItemStack(material);
        apply(item, size);
        return item;
    }

    public void apply(ItemStack item, int size) {
        requireAllowedMaterial(item.getType());
        requireValidSize(size);

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.areaSizeKey(), PersistentDataType.INTEGER, size);
        meta.displayName(plain(Component.text(size + "x" + size + " " + materialName(item.getType()),
                size == 5 ? NamedTextColor.GOLD : NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)));

        List<Component> lore = meta.lore() == null
                ? new ArrayList<>() : new ArrayList<>(meta.lore());
        removePluginLore(lore);
        lore.add(areaLore(size));
        lore.add(singleBlockLore());
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    public OptionalInt areaSizeOf(ItemStack item) {
        if (item == null || !isAllowedMaterial(item.getType()) || !item.hasItemMeta()) {
            return OptionalInt.empty();
        }
        Integer size = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.areaSizeKey(), PersistentDataType.INTEGER);
        return size != null && isValidSize(size) ? OptionalInt.of(size) : OptionalInt.empty();
    }

    public boolean sneakForSingleBlock() {
        return sneakForSingleBlock;
    }

    public boolean requireCorrectTool() {
        return requireCorrectTool;
    }

    public boolean shiftVerticalAreaAboveFloor() {
        return shiftVerticalAreaAboveFloor;
    }

    public boolean protectBlockUnderPlayer() {
        return protectBlockUnderPlayer;
    }

    public static boolean isValidSize(int size) {
        return size == 3 || size == 5;
    }

    public static boolean isAllowedMaterial(Material material) {
        return material == Material.IRON_PICKAXE
                || material == Material.DIAMOND_PICKAXE
                || material == Material.NETHERITE_PICKAXE;
    }

    public static Material parseMaterial(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "iron", "iron_pickaxe", "鉄" -> Material.IRON_PICKAXE;
            case "diamond", "diamond_pickaxe", "ダイヤ", "ダイヤモンド" -> Material.DIAMOND_PICKAXE;
            case "netherite", "netherite_pickaxe", "ネザライト" -> Material.NETHERITE_PICKAXE;
            default -> null;
        };
    }

    public static String materialName(Material material) {
        return switch (material) {
            case IRON_PICKAXE -> "鉄の範囲つるはし";
            case DIAMOND_PICKAXE -> "ダイヤモンドの範囲つるはし";
            case NETHERITE_PICKAXE -> "ネザライトの範囲つるはし";
            default -> "範囲つるはし";
        };
    }

    private static void requireValidSize(int size) {
        if (!isValidSize(size)) {
            throw new IllegalArgumentException("採掘範囲は3または5である必要があります: " + size);
        }
    }

    private static void requireAllowedMaterial(Material material) {
        if (!isAllowedMaterial(material)) {
            throw new IllegalArgumentException("鉄・ダイヤモンド・ネザライトのつるはしだけが対象です");
        }
    }

    private static void removePluginLore(List<Component> lore) {
        lore.removeIf(line -> line.equals(areaLore(3))
                || line.equals(areaLore(5))
                || line.equals(singleBlockLore()));
    }

    private static Component areaLore(int size) {
        return plain(Component.text("採掘範囲: " + size + "x" + size, NamedTextColor.YELLOW));
    }

    private static Component singleBlockLore() {
        return plain(Component.text("スニーク中: 1ブロック採掘", NamedTextColor.GRAY));
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
