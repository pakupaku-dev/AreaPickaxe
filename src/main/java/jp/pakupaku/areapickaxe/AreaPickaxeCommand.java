package jp.pakupaku.areapickaxe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AreaPickaxeCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SIZES = List.of("3", "5");
    private static final List<String> MATERIALS = List.of("iron", "diamond", "netherite");
    private static final List<String> AMOUNTS = List.of("1", "2", "4", "8", "16");

    private final AreaPickaxePlugin plugin;
    private final AreaPickaxeService pickaxeService;

    public AreaPickaxeCommand(AreaPickaxePlugin plugin, AreaPickaxeService pickaxeService) {
        this.plugin = plugin;
        this.pickaxeService = pickaxeService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, label, args);
            case "apply" -> apply(sender, label, args);
            case "reload" -> reload(sender);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("areapickaxe.give")) {
            error(sender, "このコマンドを使う権限がありません");
            return true;
        }
        if (args.length < 3) {
            sendGiveUsage(sender, label);
            return true;
        }

        Player target;
        int sizeIndex;
        Integer firstNumber = parseInteger(args[1]);
        if (sender instanceof Player player && firstNumber != null) {
            target = player;
            sizeIndex = 1;
        } else {
            if (args.length < 4) {
                sendGiveUsage(sender, label);
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                error(sender, "プレイヤー「" + args[1] + "」はオンラインではありません");
                return true;
            }
            sizeIndex = 2;
        }

        Integer size = parseInteger(args[sizeIndex]);
        if (size == null || !AreaPickaxeService.isValidSize(size)) {
            error(sender, "採掘範囲は3または5で指定してください");
            return true;
        }
        Material material = AreaPickaxeService.parseMaterial(args[sizeIndex + 1]);
        if (material == null) {
            error(sender, "素材はiron、diamond、netheriteのいずれかを指定してください");
            return true;
        }

        int amount = 1;
        if (args.length > sizeIndex + 2) {
            Integer parsedAmount = parseInteger(args[sizeIndex + 2]);
            if (parsedAmount == null || parsedAmount < 1 || parsedAmount > 16) {
                error(sender, "個数は1～16で指定してください");
                return true;
            }
            amount = parsedAmount;
        }

        ItemStack item = pickaxeService.createPickaxe(material, size);
        item.setAmount(amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        leftovers.values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));

        sender.sendMessage(Component.text(target.getName() + " に " + size + "x" + size + " "
                + AreaPickaxeService.materialName(material) + " を " + amount + " 個渡しました",
                NamedTextColor.GREEN));
        if (!sender.equals(target)) {
            target.sendMessage(Component.text(size + "x" + size + " "
                    + AreaPickaxeService.materialName(material) + " を受け取りました",
                    NamedTextColor.GOLD));
        }
        return true;
    }

    private boolean apply(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("areapickaxe.apply")) {
            error(sender, "このコマンドを使う権限がありません");
            return true;
        }
        if (!(sender instanceof Player player)) {
            error(sender, "このコマンドはゲーム内で実行してください");
            return true;
        }
        if (args.length != 2) {
            error(sender, "使い方: /" + label + " apply <3|5>");
            return true;
        }
        Integer size = parseInteger(args[1]);
        if (size == null || !AreaPickaxeService.isValidSize(size)) {
            error(sender, "採掘範囲は3または5で指定してください");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!AreaPickaxeService.isAllowedMaterial(held.getType())) {
            error(sender, "鉄・ダイヤモンド・ネザライトのつるはしをメインハンドに持ってください");
            return true;
        }
        pickaxeService.apply(held, size);
        sender.sendMessage(Component.text("手持ちのつるはしに" + size + "x" + size
                + "範囲採掘を付与しました", NamedTextColor.GREEN));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("areapickaxe.reload")) {
            error(sender, "このコマンドを使う権限がありません");
            return true;
        }
        plugin.reloadConfig();
        pickaxeService.reload();
        sender.sendMessage(Component.text("AreaPickaxeの設定を再読み込みしました",
                NamedTextColor.GREEN));
        return true;
    }

    private static void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("AreaPickaxe コマンド", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " give <3|5> <iron|diamond|netherite> [個数]",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label
                + " give <プレイヤー> <3|5> <iron|diamond|netherite> [個数]",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " apply <3|5>", NamedTextColor.YELLOW)
                .append(Component.text(" - 手持ちのつるはしへ付与", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW));
    }

    private static void sendGiveUsage(CommandSender sender, String label) {
        error(sender, "使い方: /" + label
                + " give [プレイヤー] <3|5> <iron|diamond|netherite> [個数]");
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private static @Nullable Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("areapickaxe.give")) {
                candidates.add("give");
            }
            if (sender.hasPermission("areapickaxe.apply")) {
                candidates.add("apply");
            }
            if (sender.hasPermission("areapickaxe.reload")) {
                candidates.add("reload");
            }
        } else if (args[0].equalsIgnoreCase("apply") && args.length == 2) {
            candidates.addAll(SIZES);
        } else if (args[0].equalsIgnoreCase("give")) {
            addGiveCandidates(sender, args, candidates);
        }

        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        candidates.removeIf(candidate -> !candidate.toLowerCase(Locale.ROOT).startsWith(current));
        Collections.sort(candidates);
        return candidates;
    }

    private static void addGiveCandidates(CommandSender sender, String[] args,
                                           List<String> candidates) {
        if (args.length == 2) {
            if (sender instanceof Player) {
                candidates.addAll(SIZES);
            }
            Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
            return;
        }

        boolean selfSyntax = sender instanceof Player && parseInteger(args[1]) != null;
        int sizeIndex = selfSyntax ? 1 : 2;
        int materialIndex = sizeIndex + 1;
        int amountIndex = sizeIndex + 2;
        if (args.length - 1 == sizeIndex) {
            candidates.addAll(SIZES);
        } else if (args.length - 1 == materialIndex) {
            candidates.addAll(MATERIALS);
        } else if (args.length - 1 == amountIndex) {
            candidates.addAll(AMOUNTS);
        }
    }
}
