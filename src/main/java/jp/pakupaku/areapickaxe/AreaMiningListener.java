package jp.pakupaku.areapickaxe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class AreaMiningListener implements Listener {
    private final AreaPickaxePlugin plugin;
    private final AreaPickaxeService pickaxeService;
    private final Set<UUID> processingPlayers = new HashSet<>();

    public AreaMiningListener(AreaPickaxePlugin plugin, AreaPickaxeService pickaxeService) {
        this.plugin = plugin;
        this.pickaxeService = pickaxeService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (processingPlayers.contains(player.getUniqueId())) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        OptionalInt sizeResult = pickaxeService.areaSizeOf(tool);
        if (sizeResult.isEmpty()) {
            return;
        }
        if (!player.hasPermission("areapickaxe.use")) {
            player.sendActionBar(Component.text("範囲採掘を使う権限がありません", NamedTextColor.RED));
            return;
        }
        if (pickaxeService.sneakForSingleBlock() && player.isSneaking()) {
            return;
        }
        if (!isPickaxeMineable(event.getBlock().getType())) {
            return;
        }

        int size = sizeResult.getAsInt();
        Block center = event.getBlock();
        MiningAxis axis = determineAxis(player, center);
        int heldSlot = player.getInventory().getHeldItemSlot();

        // MONITORでキャンセルされていないことを確認後、次のtickに周囲を採掘します。
        plugin.getServer().getScheduler().runTask(plugin,
                () -> mineArea(player, center, axis, size, heldSlot));
    }

    private void mineArea(Player player, Block center, MiningAxis axis, int size, int heldSlot) {
        if (!player.isOnline() || !player.getWorld().equals(center.getWorld())
                || player.getInventory().getHeldItemSlot() != heldSlot
                || !player.hasPermission("areapickaxe.use")
                || player.getLocation().distanceSquared(center.getLocation().add(0.5, 0.5, 0.5)) > 100.0) {
            return;
        }

        OptionalInt currentSize = pickaxeService.areaSizeOf(player.getInventory().getItemInMainHand());
        if (currentSize.isEmpty() || currentSize.getAsInt() != size) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!processingPlayers.add(playerId)) {
            return;
        }
        try {
            for (Block block : collectBlocks(center, axis, size, player.getLocation().getBlockY(),
                    pickaxeService.shiftVerticalAreaAboveFloor())) {
                ItemStack currentTool = player.getInventory().getItemInMainHand();
                OptionalInt toolSize = pickaxeService.areaSizeOf(currentTool);
                if (toolSize.isEmpty() || toolSize.getAsInt() != size) {
                    break; // 耐久切れ、持ち替え、または効果が除去された場合
                }

                Material type = block.getType();
                if (!isPickaxeMineable(type) || type.getHardness() < 0.0f) {
                    continue;
                }
                if (pickaxeService.protectBlockUnderPlayer()
                        && isBlockUnderPlayer(player, block)) {
                    continue;
                }
                if (pickaxeService.requireCorrectTool() && !block.isPreferredTool(currentTool)) {
                    continue;
                }

                // Paper APIがBlockBreakEvent、エンチャント、ドロップ、経験値、耐久値を処理します。
                player.breakBlock(block);
            }
        } finally {
            processingPlayers.remove(playerId);
        }
    }

    private static List<Block> collectBlocks(Block center, MiningAxis axis, int size,
                                             int playerFeetY, boolean shiftAboveFloor) {
        int radius = size / 2;
        World world = center.getWorld();
        List<BlockOffset> offsets = new ArrayList<>(size * size);

        for (int first = -radius; first <= radius; first++) {
            for (int second = -radius; second <= radius; second++) {
                offsets.add(new BlockOffset(first, second));
            }
        }
        offsets.sort(Comparator.comparingInt(offset -> offset.first() * offset.first()
                + offset.second() * offset.second()));

        int verticalShift = 0;
        if (shiftAboveFloor && axis != MiningAxis.Y) {
            int lowestY = center.getY() - radius;
            verticalShift = Math.min(radius, Math.max(0, playerFeetY - lowestY));
        }

        List<Block> blocks = new ArrayList<>(size * size - 1);
        for (BlockOffset offset : offsets) {
            int x = center.getX();
            int y = center.getY();
            int z = center.getZ();
            switch (axis) {
                case X -> {
                    y += offset.first() + verticalShift;
                    z += offset.second();
                }
                case Y -> {
                    x += offset.first();
                    z += offset.second();
                }
                case Z -> {
                    x += offset.first();
                    y += offset.second() + verticalShift;
                }
            }

            if (x == center.getX() && y == center.getY() && z == center.getZ()) {
                continue;
            }
            if (y < world.getMinHeight() || y >= world.getMaxHeight()
                    || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            blocks.add(world.getBlockAt(x, y, z));
        }
        return blocks;
    }

    private static boolean isBlockUnderPlayer(Player player, Block block) {
        Block supportingBlock = player.getLocation().subtract(0.0, 0.05, 0.0).getBlock();
        return supportingBlock.equals(block);
    }

    private static MiningAxis determineAxis(Player player, Block center) {
        RayTraceResult trace = player.rayTraceBlocks(8.0, FluidCollisionMode.NEVER);
        if (trace != null && trace.getHitBlock() != null
                && trace.getHitBlock().equals(center) && trace.getHitBlockFace() != null) {
            BlockFace face = trace.getHitBlockFace();
            if (face == BlockFace.UP || face == BlockFace.DOWN) {
                return MiningAxis.Y;
            }
            if (face == BlockFace.EAST || face == BlockFace.WEST) {
                return MiningAxis.X;
            }
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                return MiningAxis.Z;
            }
        }

        Vector direction = player.getEyeLocation().getDirection();
        double x = Math.abs(direction.getX());
        double y = Math.abs(direction.getY());
        double z = Math.abs(direction.getZ());
        if (y >= x && y >= z) {
            return MiningAxis.Y;
        }
        return x >= z ? MiningAxis.X : MiningAxis.Z;
    }

    private static boolean isPickaxeMineable(Material material) {
        return material.isBlock() && !material.isAir() && Tag.MINEABLE_PICKAXE.isTagged(material);
    }

    private enum MiningAxis {
        X, Y, Z
    }

    private record BlockOffset(int first, int second) {
    }
}
