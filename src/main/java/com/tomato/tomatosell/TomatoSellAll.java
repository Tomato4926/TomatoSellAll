package com.tomato.tomatosellall;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TomatoSellAll extends JavaPlugin implements Listener {

    private Economy econ;
    private final Map<UUID, BukkitTask> pendingConfirmations = new HashMap<>();

    @Override
    public void onEnable() {
        setupEconomy();
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("未找到Vault或EssentialsX，插件已禁用！");
            return false;
        }
        econ = rsp.getProvider();
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("sellall")) return false;

        if (!(sender instanceof Player)) {
            sendMessage(sender, "not-player");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            startConfirmation(player);
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "yes":
                    confirmSell(player);
                    return true;
                case "no":
                    cancelSell(player);
                    return true;
            }
        }

        return false;
    }

    private void startConfirmation(Player player) {
        UUID uuid = player.getUniqueId();

        if (pendingConfirmations.containsKey(uuid)) {
            pendingConfirmations.get(uuid).cancel();
        }

        sendFormattedMessage(player, "warning",
                "price", String.format("%.2f", getPrice()));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (pendingConfirmations.remove(uuid) != null) {
                sendMessage(player, "timeout");
            }
        }, 30 * 20L);

        pendingConfirmations.put(uuid, task);
    }

    private void confirmSell(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = pendingConfirmations.remove(uuid);

        if (task == null) {
            sendMessage(player, "no-pending");
            return;
        }

        task.cancel();
        int itemCount = calculateAndClearInventory(player);
        double total = itemCount * getPrice();
        econ.depositPlayer(player, total);
        sendFormattedMessage(player, "success",
                "amount", String.format("%.2f", total));
    }

    private void cancelSell(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = pendingConfirmations.remove(uuid);

        if (task != null) {
            task.cancel();
            sendMessage(player, "cancel");
        } else {
            sendMessage(player, "no-pending");
        }
    }

    private int calculateAndClearInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        int count = 0;

        // 主背包
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                count += item.getAmount();
                inv.setItem(i, null);
            }
        }

        // 盔甲栏
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                count += armor.getAmount();
            }
        }
        inv.setArmorContents(new ItemStack[4]);

        // 副手
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand.getType() != Material.AIR) {
            count += offhand.getAmount();
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        }

        return count;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BukkitTask task = pendingConfirmations.remove(uuid);
        if (task != null) task.cancel();
    }

    private double getPrice() {
        return getConfig().getDouble("settings.price-per-item", 1.0);
    }

    private void sendMessage(CommandSender sender, String path) {
        String message = getConfig().getString("messages." + path, "");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private void sendFormattedMessage(CommandSender sender, String path, Object... replacements) {
        String message = getConfig().getString("messages." + path, "");
        for (int i = 0; i < replacements.length; i += 2) {
            String key = replacements[i].toString();
            String value = replacements[i + 1].toString();
            message = message.replace("{" + key + "}", value);
        }
        String[] lines = ChatColor.translateAlternateColorCodes('&', message).split("\n");
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }
}
