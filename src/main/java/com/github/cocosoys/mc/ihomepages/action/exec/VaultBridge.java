package com.github.cocosoys.mc.ihomepages.action.exec;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Vault 可选桥：通过反射探测 Vault 经济服务，用于余额预检。
 *
 * <p>设计目标：<b>零编译依赖</b>——服务器未装 Vault 时本类自动禁用（预检跳过），
 * 经济操作本身走 {@code eco take/give} 命令，不依赖 Vault。装了 Vault 则 {@code balance-check: true}
 * 的动作会在扣款前预检余额。</p>
 */
public final class VaultBridge {

    private static Boolean available = null;
    private static Class<?> economyClass = null;

    private VaultBridge() {
    }

    /** Vault 经济服务是否可用（探测一次并缓存）。 */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            try {
                economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                available = Bukkit.getServicesManager().getRegistration(economyClass) != null;
            } catch (Throwable t) {
                economyClass = null;
                available = false;
            }
        }
        return available;
    }

    /**
     * 查询离线/在线玩家余额（Vault API 反射）。
     *
     * @return 余额；Vault 不可用返回 -1（调用方应跳过预检）
     */
    public static double getBalance(String playerName) {
        if (!isAvailable() || playerName == null) {
            return -1D;
        }
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
            Object registration = Bukkit.getServicesManager().getRegistration(economyClass);
            Object provider = registration.getClass().getMethod("getProvider").invoke(registration);
            Object balance = economyClass.getMethod("getBalance", OfflinePlayer.class).invoke(provider, op);
            return ((Number) balance).doubleValue();
        } catch (Throwable t) {
            return -1D;
        }
    }
}
