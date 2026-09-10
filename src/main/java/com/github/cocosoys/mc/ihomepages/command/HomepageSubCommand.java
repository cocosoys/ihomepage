package com.github.cocosoys.mc.ihomepages.command;

import org.bukkit.command.CommandSender;

import com.github.cocosoys.mc.soyshttpovermc.HttpOverMcPlugin;
import com.github.cocosoys.mc.soyshttpovermc.command.SubCommand;
import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.ihomepages.action.WebActionManager;
import com.github.cocosoys.mc.ihomepages.action.model.WebAction;
import com.github.cocosoys.mc.ihomepages.action.queue.OfflineTaskQueue;
import com.github.cocosoys.mc.ihomepages.api.HomeApi;

import java.util.ArrayList;
import java.util.List;

/**
 * /soyshttp homepage 子指令：首页列表查看与切换管理 + 网页动作运维（归属 ihomepages 主页模块）。
 *
 * <pre>
 *   /soyshttp homepage list            —— 列出所有已记录的主页位置
 *   /soyshttp homepage set &lt;名称&gt;     —— 切换到指定主页（写 homepage.current + 同步 web.home + reload）
 *   /soyshttp homepage info            —— 显示当前主页名称
 *   /soyshttp homepage reload          —— 重新应用当前主页（按 homepage.current 刷新 web.home，不切换）
 *   /soyshttp homepage actions list    —— 列出全部网页动作（id / name / price / offline / 状态）
 *   /soyshttp homepage actions validate—— 全量校验 actions.yml 并输出每个动作的通过/错误明细
 *   /soyshttp homepage actions reload  —— 热重载 actions.yml（不重启，不丢已入队任务）
 *   /soyshttp homepage actions queue &lt;玩家&gt; —— 查看某玩家的待补发队列
 *   /soyshttp homepage actions flush &lt;玩家&gt; —— 手动触发补发某玩家（等价上线补发）
 * </pre>
 */
public class HomepageSubCommand extends SubCommand {

    private final HomeApi homeApi;
    private final WebActionManager actionManager;
    private final OfflineTaskQueue taskQueue;

    public HomepageSubCommand(HttpOverMcPlugin plugin, HomeApi homeApi,
                              WebActionManager actionManager, OfflineTaskQueue taskQueue) {
        super(plugin);
        this.homeApi = homeApi;
        this.actionManager = actionManager;
        this.taskQueue = taskQueue;
    }

    @Override
    public String name() {
        return "homepage";
    }

    @Override
    public boolean requireOp() {
        return true;
    }

    @Override
    public String usage() {
        return I18n.t("command.homepage.usage",
                "/soyshttp homepage <list|set <name>|info|reload|actions ...> —— 首页与网页动作管理");
    }

    @Override
    public String detail() {
        return I18n.t("command.homepage.detail",
                "首页列表查看与切换管理 + 网页动作运维\n" +
                "  /soyshttp homepage list              —— 列出所有已记录的主页位置\n" +
                "  /soyshttp homepage set <名称>       —— 切换到指定主页（写 homepage.current + 同步 web.home + reload）\n" +
                "  /soyshttp homepage info              —— 显示当前主页名称\n" +
                "  /soyshttp homepage reload            —— 重新应用当前主页（按 homepage.current 刷新 web.home，不切换）\n" +
                "  /soyshttp homepage actions list      —— 列出全部网页动作\n" +
                "  /soyshttp homepage actions validate  —— 校验 actions.yml 配置\n" +
                "  /soyshttp homepage actions reload    —— 热重载 actions.yml\n" +
                "  /soyshttp homepage actions queue <玩家> —— 查看某玩家的待补发队列\n" +
                "  /soyshttp homepage actions flush <玩家> —— 手动补发某玩家\n" +
                "示例:\n" +
                "  /soyshttp homepage list\n" +
                "  /soyshttp homepage actions reload\n" +
                "  /soyshttp homepage actions queue Steve");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            msgT(sender, "command.homepage.usage-short", "用法: /{0} {1}", label, usage());
            return;
        }

        if (homeApi == null) {
            msgT(sender, "command.homepage.uninit", "§c首页模块尚未初始化。");
            return;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {
            case "list": {
                List<String> names = homeApi.list();
                if (names.isEmpty()) {
                    msgT(sender, "command.homepage.none", "§e当前没有已记录的主页。");
                } else {
                    msgT(sender, "command.homepage.list-title", "§a已记录主页列表:");
                    String current = homeApi.getCurrent();
                    for (String n : names) {
                        boolean isCurrent = n.equals(current);
                        String marker = isCurrent
                                ? I18n.t("command.homepage.current-marker", " §b← 当前")
                                : "";
                        msgT(sender, "command.homepage.item", "  §7- §f{0}{1}", n, marker);
                    }
                }
                break;
            }
            case "set": {
                if (args.length < 3) {
                    msgT(sender, "command.homepage.set-usage", "§c用法: /{0} homepage set <首页名称>", label);
                    return;
                }
                String target = args[2];
                if (homeApi.switchTo(target)) {
                    msgT(sender, "command.homepage.switched", "§a已切换到主页: §f{0}§a（已同步 web.home 并 reload）", target);
                } else {
                    msgT(sender, "command.homepage.not-found",
                            "§c未找到名为 '{0}' 的主页。可用 '/{1} homepage list' 查看所有已记录的主页。",
                            target, label);
                }
                break;
            }
            case "info": {
                String cur = homeApi.getCurrent();
                if (cur == null) {
                    msgT(sender, "command.homepage.no-current", "§e当前未设置主页。");
                } else {
                    msgT(sender, "command.homepage.current", "§a当前主页: §f{0}", cur);
                }
                break;
            }
            case "reload": {
                // 按 homepage.current 重新应用 web.home（不切换）；等价于 /soyshttp reload 中对本模块的钩子动作
                plugin.getDelegate().reloadHttpConfig();
                msgT(sender, "command.homepage.reloaded", "§a已按当前主页重新应用 web.home。");
                break;
            }
            case "actions": {
                handleActions(sender, label, args);
                break;
            }
            default: {
                msgT(sender, "command.homepage.unknown",
                        "§c未知子指令: {0}。可用: list / set <name> / info / reload / actions ...", sub);
            }
        }
    }

    /** actions 运维命令组：list / validate / reload / queue <玩家> / flush <玩家>。 */
    private void handleActions(CommandSender sender, String label, String[] args) {
        if (actionManager == null) {
            msgT(sender, "command.homepage.uninit", "§c网页动作模块尚未初始化。");
            return;
        }
        String sub = args.length < 3 ? "" : args[2].toLowerCase();
        switch (sub) {
            case "list": {
                if (actionManager.all().isEmpty()) {
                    msgT(sender, "command.homepage.actions-empty", "§e当前没有已加载的网页动作（检查 actions.yml 配置）。");
                    return;
                }
                msgT(sender, "command.homepage.actions-title", "§a已加载网页动作 (" + actionManager.all().size() + "):");
                for (WebAction a : actionManager.all().values()) {
                    String vis = a.isVisible() ? "" : " §7[隐藏]";
                    msgT(sender, "command.homepage.actions-item",
                            "  §7- §f{0} §7价格:§f{1} §7离线:§f{2}§7{3}",
                            a.getId(), fmtPrice(a.getPrice()), a.getOffline(), vis);
                }
                break;
            }
            case "validate": {
                List<String> errors = actionManager.lastErrors();
                if (errors.isEmpty()) {
                    msgT(sender, "command.homepage.actions-valid",
                            "§aactions.yml 校验通过，已加载 {0} 个动作。", actionManager.all().size());
                } else {
                    msgT(sender, "command.homepage.actions-errors-title",
                            "§cactions.yml 存在 {0} 处错误:", errors.size());
                    for (String e : errors) {
                        msgT(sender, "command.homepage.actions-error", "  §c- {0}", e);
                    }
                }
                break;
            }
            case "reload": {
                actionManager.reload();
                msgT(sender, "command.homepage.actions-reloaded",
                        "§a已热重载 actions.yml（当前 {0} 个动作，{1} 处错误；已入队任务不受影响）。",
                        actionManager.all().size(), actionManager.lastErrors().size());
                break;
            }
            case "queue": {
                String player = args.length < 4 ? null : args[3];
                if (player == null || player.isEmpty()) {
                    msgT(sender, "command.homepage.actions-queue-usage", "§c用法: /{0} homepage actions queue <玩家名>", label);
                    return;
                }
                int pending = taskQueue == null ? 0 : taskQueue.pendingCount(player);
                msgT(sender, "command.homepage.actions-queue-result",
                        "§a玩家 {0} 当前有 {1} 条待补发的离线任务。", player, pending);
                break;
            }
            case "flush": {
                String player = args.length < 4 ? null : args[3];
                if (player == null || player.isEmpty()) {
                    msgT(sender, "command.homepage.actions-flush-usage", "§c用法: /{0} homepage actions flush <玩家名>", label);
                    return;
                }
                int n = 0;
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayerExact(player);
                if (p != null && taskQueue != null) {
                    n = taskQueue.deliver(p);
                }
                if (p == null) {
                    msgT(sender, "command.homepage.actions-flush-offline",
                            "§e玩家 {0} 不在线，无法补发（上线时将自动补发）。", player);
                } else {
                    msgT(sender, "command.homepage.actions-flush-result",
                            "§a已补发玩家 {0} 的离线任务 {1} 条。", player, n);
                }
                break;
            }
            default: {
                msgT(sender, "command.homepage.actions-usage",
                        "§c用法: /{0} homepage actions <list|validate|reload|queue <玩家>|flush <玩家>>", label);
            }
        }
    }

    private static String fmtPrice(double price) {
        long l = (long) price;
        return l == price ? String.valueOf(l) : String.valueOf(price);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            // 二级补全：list / set / info / reload / actions
            List<String> out = new ArrayList<>();
            String prefix = args[1].toLowerCase();
            for (String opt : new String[]{"list", "set", "info", "reload", "actions"}) {
                if (opt.startsWith(prefix)) out.add(opt);
            }
            return out;
        }
        if (args.length == 3 && "set".equalsIgnoreCase(args[1]) && homeApi != null) {
            // 三级补全：set 后面的主页名称
            String prefix = args[2].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String name : homeApi.list()) {
                if (name.toLowerCase().startsWith(prefix)) out.add(name);
            }
            return out;
        }
        if (args.length == 3 && "actions".equalsIgnoreCase(args[1])) {
            // 三级补全：actions 子命令
            List<String> out = new ArrayList<>();
            String prefix = args[2].toLowerCase();
            for (String opt : new String[]{"list", "validate", "reload", "queue", "flush"}) {
                if (opt.startsWith(prefix)) out.add(opt);
            }
            return out;
        }
        if (args.length == 4 && "actions".equalsIgnoreCase(args[1])
                && ("queue".equalsIgnoreCase(args[2]) || "flush".equalsIgnoreCase(args[2]))) {
            // 四级补全：玩家名提示
            String prefix = args[3].toLowerCase();
            List<String> out = new ArrayList<>();
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) out.add(p.getName());
            }
            return out;
        }
        return java.util.Collections.emptyList();
    }
}
