package jp.kamei.areapickaxe;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AreaPickaxePlugin extends JavaPlugin {
    private NamespacedKey areaSizeKey;
    private AreaPickaxeService pickaxeService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        areaSizeKey = new NamespacedKey(this, "area_size");

        pickaxeService = new AreaPickaxeService(this);
        pickaxeService.reload();

        getServer().getPluginManager().registerEvents(
                new AreaMiningListener(this, pickaxeService), this);

        AreaPickaxeCommand commandHandler = new AreaPickaxeCommand(this, pickaxeService);
        PluginCommand command = getCommand("areapickaxe");
        if (command == null) {
            throw new IllegalStateException("plugin.ymlにareapickaxeコマンドがありません");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("AreaPickaxe 1.0.0 を有効化しました（3x3・5x5）");
    }

    public NamespacedKey areaSizeKey() {
        return areaSizeKey;
    }
}
