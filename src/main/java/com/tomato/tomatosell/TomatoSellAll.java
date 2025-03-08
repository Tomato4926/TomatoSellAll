// TomatoSellAll.java
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
    private Economy econ = null;
    private final Map<UUID, BukkitTask> pendingConfirmations = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("未找到Vault经济系统，插件已禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("TomatoSellAll 已加载！");
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("sellall")) return false;

        if (!(sender instanceof Player)) {
            sender.sendMessage("只有玩家可以使用此命令！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            handleSellAllCommand(player);
            return true;
        } else if (args.length == 1) {
            if (args[0].equalsIgnoreCase("yes")) {
                handleConfirmation(player);
                return true;
            } else if (args[0].equalsIgnoreCase("no")) {
                handleCancel(player);
                return true;
            }
        }
        return false;
    }

    private void handleSellAllCommand(Player player) {
        UUID playerId = player.getUniqueId();

        // 取消已有的确认任务
        if (pendingConfirmations.containsKey(playerId)) {
            pendingConfirmations.get(playerId).cancel();
        }

        // 发送警告消息
        String warning = ChatColor.translateAlternateColorCodes('&',
                "&c&l警告：此操作会售出你目前背包内的所有物品，包括盔甲栏和副手栏，并按售出物品数量给予云币，每个物品1云币。\n" +
                        "&c&l此操作的目的是将商店中不能出售的物品也换为云币，如果你确定此操作，请在30秒内输入/sellall yes。本操作不可撤销！\n" +
                        "&c&l如果你想取消，请输入/sellall no。");
        player.sendMessage(warning);

        // 创建新的确认任务
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            if (pendingConfirmations.remove(playerId) != null) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&l你已放弃出售背包内所有物品。"));
            }
        }, 30 * 20L); // 30秒 = 30*20 ticks

        pendingConfirmations.put(playerId, task);
    }

    private void handleConfirmation(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = pendingConfirmations.remove(playerId);

        if (task == null) {
            player.sendMessage(ChatColor.RED + "请先使用/sellall确认操作！");
            return;
        }

        task.cancel();
        int totalItems = calculateAndClearInventory(player);
        econ.depositPlayer(player, totalItems);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&l你背包内的所有物品已出售！"));
    }

    private void handleCancel(Player player) {
        UUID playerId = player.getUniqueId();
        BukkitTask task = pendingConfirmations.remove(playerId);

        if (task != null) {
            task.cancel();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&l你已取消出售背包内所有物品。"));
        } else {
            player.sendMessage(ChatColor.RED + "你没有待确认的出售操作。");
        }
    }

    // 玩家下线时清除状态
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask task = pendingConfirmations.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private int calculateAndClearInventory(Player player) {
        // 保持原有实现不变
        PlayerInventory inv = player.getInventory();
        int total = 0;

        // 处理主背包
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                total += item.getAmount();
                inv.setItem(i, null);
            }
        }

        // 处理盔甲栏
        for (ItemStack armor : inv.getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                total += armor.getAmount();
            }
        }
        inv.setArmorContents(new ItemStack[4]);

        // 处理副手
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand.getType() != Material.AIR) {
            total += offhand.getAmount();
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        }

        return total;
    }
}