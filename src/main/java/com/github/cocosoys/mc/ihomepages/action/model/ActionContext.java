package com.github.cocosoys.mc.ihomepages.action.model;

/**
 * 一次动作执行的环境上下文：由 {@code WebActionExecutor} 在执行前构建并传给效果执行器。
 * 全部字段只读（执行期间不变）。
 */
public class ActionContext {

    /** 领取主体：玩家名（会话凭证解析，在线/离线均可）。 */
    private final String playerName;

    /** 玩家 UUID（离线用 getOfflinePlayer 解析；可能为 null，仅审计）。 */
    private final String playerUuid;

    /** 玩家此刻是否在线（执行入口判定一次，全程使用）。 */
    private final boolean online;

    /** 前端传入的数量（默认 1，用于 {amount} 占位符与计价）。 */
    private final int amount;

    /** 正在执行的动作定义。 */
    private final WebAction action;

    /** 客户端 IP（审计；可为 null）。 */
    private final String ip;

    public ActionContext(String playerName, String playerUuid, boolean online, int amount,
                         WebAction action, String ip) {
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.online = online;
        this.amount = amount;
        this.action = action;
        this.ip = ip;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public boolean isOnline() {
        return online;
    }

    public int getAmount() {
        return amount;
    }

    public WebAction getAction() {
        return action;
    }

    public String getIp() {
        return ip;
    }
}
