package com.github.cocosoys.mc.ihomepages.action;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.ihomepages.action.exec.ActionException;
import com.github.cocosoys.mc.ihomepages.action.exec.CommandEffectExecutor;
import com.github.cocosoys.mc.ihomepages.action.exec.CommandRunner;
import com.github.cocosoys.mc.ihomepages.action.exec.EconomyEffectExecutor;
import com.github.cocosoys.mc.ihomepages.action.exec.EffectExecutor;
import com.github.cocosoys.mc.ihomepages.action.exec.VaultBridge;
import com.github.cocosoys.mc.ihomepages.action.model.ActionClaimRecord;
import com.github.cocosoys.mc.ihomepages.action.model.ActionContext;
import com.github.cocosoys.mc.ihomepages.action.model.ActionResult;
import com.github.cocosoys.mc.ihomepages.action.model.WebAction;
import com.github.cocosoys.mc.ihomepages.action.model.WebActionEffect;
import com.github.cocosoys.mc.ihomepages.action.queue.ActionClaimStore;
import com.github.cocosoys.mc.ihomepages.action.queue.OfflineTaskQueue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页动作执行门面 —— 对外统一入口，也是「基础方法类」的调用面。
 *
 * <p><b>开发者用法</b>（后续业务直接调用，无需重复实现执行链）：</p>
 * <pre>
 *   executor.execute("daily-bonus", playerName);        // 标准执行（数量 1）
 *   executor.execute("buy-diamond", playerName, 5);     // 带数量（扣款 = price × 5）
 *   executor.register(new MyEffectExecutor());          // 注册新效果类型（SPI 扩展）
 * </pre>
 *
 * <p>执行链：身份/动作校验 → 冷却 → 计价 → 可选余额预检（Vault）→ 逐效果执行
 * （command 经 CommandRunner 在线立即/离线分流，economy 走经济命令）→ 结果聚合 → 审计日志。</p>
 */
public class WebActionExecutor {

    private final JavaPlugin plugin;
    private final WebActionManager manager;
    private final CommandRunner runner;
    private final OfflineTaskQueue taskQueue;
    private final ActionClaimStore claimStore;
    private final Map<String, EffectExecutor> effectRegistry = new LinkedHashMap<>();

    /** 冷却记录：key = playerName|actionId，value = 上次执行时刻（ms）。 */
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    /** 领取型动作的 per-player+action 锁：串行化「查去重 → 执行 → 落记录」，杜绝并发双领。 */
    private final ConcurrentHashMap<String, Object> claimLocks = new ConcurrentHashMap<>();

    public WebActionExecutor(JavaPlugin plugin, WebActionManager manager,
                             CommandRunner runner, OfflineTaskQueue taskQueue) {
        this(plugin, manager, runner, taskQueue, new ActionClaimStore());
    }

    public WebActionExecutor(JavaPlugin plugin, WebActionManager manager,
                             CommandRunner runner, OfflineTaskQueue taskQueue,
                             ActionClaimStore claimStore) {
        this.plugin = plugin;
        this.manager = manager;
        this.runner = runner;
        this.taskQueue = taskQueue;
        this.claimStore = claimStore;
        register(new CommandEffectExecutor(runner));
        register(new EconomyEffectExecutor(runner));
    }

    /** 注册效果执行器（SPI 扩展点：开发者新增效果类型后服主可在 actions.yml 直接使用）。 */
    public void register(EffectExecutor executor) {
        if (executor == null) {
            return;
        }
        effectRegistry.put(executor.type(), executor);
        // economy 执行器以子类型注册（economy.take / economy.give）
        if (executor instanceof EconomyEffectExecutor) {
            effectRegistry.put("economy.take", executor);
            effectRegistry.put("economy.give", executor);
        }
    }

    /** 标准执行：动作 + 玩家名（数量 1）。 */
    public ActionResult execute(String actionId, String playerName) {
        return execute(actionId, playerName, 1);
    }

    /** 标准执行：动作 + 玩家名 + 数量（服务端计价 = price × amount）。 */
    public ActionResult execute(String actionId, String playerName, int amount) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return ActionResult.reject("invalid-player", "玩家名为空");
        }
        WebAction action = manager.get(actionId);
        if (action == null) {
            return ActionResult.reject("not-found", "动作不存在: " + actionId);
        }
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        boolean online = onlinePlayer != null;
        ActionContext ctx = new ActionContext(playerName,
                online ? onlinePlayer.getUniqueId().toString() : null,
                online, Math.max(1, amount), action, null);
        // 领取型动作（claim-period 配置）：带锁执行「查去重 → 执行 → 落记录」
        if (action.isClaimAction()) {
            return executeClaim(action, ctx);
        }
        return execute(action, ctx);
    }

    /**
     * 领取型动作执行：per-player+action 锁内完成「查去重 → 执行 → 成功落领取记录」，
     * 防止同一玩家并发请求双领（与礼包链路原 per-player 锁语义一致）。
     */
    private ActionResult executeClaim(WebAction action, ActionContext ctx) {
        String lockKey = ctx.getPlayerName() + "|" + action.getId();
        Object lock = claimLocks.computeIfAbsent(lockKey, k -> new Object());
        try {
            synchronized (lock) {
                ActionClaimRecord rec = claimStore.findLatest(ctx.getPlayerName(), action.getId());
                if (claimStore.isClaimed(rec, action.getClaimPeriod(), System.currentTimeMillis())) {
                    return ActionResult.reject("already-claimed",
                            "你已经领取过啦，本周期内不可重复领取");
                }
                ActionResult r = execute(action, ctx);
                if (r.isSuccess()) {
                    claimStore.record(ctx.getPlayerName(), action.getId(), action.getClaimPeriod());
                }
                return r;
            }
        } finally {
            claimLocks.remove(lockKey, lock);
        }
    }

    /** 内部执行（开发者亦可传入自定义上下文）。 */
    public ActionResult execute(WebAction action, ActionContext ctx) {
        if (action == null || ctx == null || ctx.getPlayerName() == null) {
            return ActionResult.reject("invalid-args", "执行参数不完整");
        }
        // 0. 时间窗口校验（未配置窗口则放行）
        if (!inClaimWindow(action.getClaimWindowStart(), action.getClaimWindowEnd())) {
            return ActionResult.reject("out-of-window", "当前不在领取时间范围内");
        }
        // 1. 冷却
        if (!passCooldown(action, ctx.getPlayerName())) {
            return ActionResult.reject("cooldown",
                    "操作过于频繁，请 " + action.getCooldownSeconds() + " 秒后再试");
        }
        // 2. 计价 + 可选余额预检（Vault）
        double totalPrice = action.getPrice() * Math.max(1, ctx.getAmount());
        if (action.isBalanceCheck() && totalPrice > 0 && VaultBridge.isAvailable()) {
            double balance = VaultBridge.getBalance(ctx.getPlayerName());
            if (balance >= 0 && balance < totalPrice) {
                return ActionResult.reject("insufficient-funds", "余额不足，需要 " + fmt(totalPrice));
            }
        }
        // 3. 逐效果执行（按配置顺序：可编排「先扣款、再发物、再给权限」）
        boolean anyQueued = false;
        boolean anyRejected = false;
        List<String> details = new ArrayList<>();
        for (WebActionEffect effect : action.getEffects()) {
            EffectExecutor executor = resolve(effect.getType());
            if (executor == null) {
                details.add("未注册效果类型: " + effect.getType());
                continue;
            }
            try {
                if (executor.execute(effect, ctx)) {
                    details.add(label(effect) + "已执行");
                } else {
                    anyQueued = true;
                    details.add(label(effect) + "已入队（上线补发）");
                }
            } catch (ActionException ex) {
                if ("rejected-offline".equals(ex.getCode())) {
                    anyRejected = true;
                    details.add(label(effect) + "被拒绝（需在线）");
                } else {
                    plugin.getLogger().warning("[actions] 玩家 " + ctx.getPlayerName()
                            + " 执行 " + action.getId() + " 效果失败: " + ex.getMessage());
                    return ActionResult.reject(ex.getCode() == null ? "effect-failed" : ex.getCode(),
                            "执行失败: " + ex.getMessage());
                }
            }
        }
        // 4. 审计日志（冷却记录已在 passCooldown 写入）
        plugin.getLogger().info("[actions] " + ctx.getPlayerName() + " 执行动作 " + action.getId()
                + "（" + (ctx.isOnline() ? "在线" : "离线") + "）: " + String.join("; ", details));

        // 5. 结果聚合
        String mode;
        String message;
        if (anyRejected && !anyQueued) {
            mode = "rejected";
            message = "操作失败：该动作需要玩家在线才能完成";
        } else if (anyQueued) {
            mode = "queued";
            message = "已立即生效；物品类奖励将在你上线后自动发放";
        } else {
            mode = "executed";
            message = "操作成功，已立即生效";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", mode);
        data.put("details", details);
        return ActionResult.success(mode, message, data);
    }

    /** 根据类型解析执行器（economy.take/give → EconomyEffectExecutor）。 */
    private EffectExecutor resolve(String type) {
        if (type == null) {
            return null;
        }
        return effectRegistry.get(type);
    }

    private static String label(WebActionEffect effect) {
        String t = effect.getType() == null ? "?" : effect.getType();
        if ("command".equals(t) && effect.getCommand() != null) {
            return "指令[" + effect.getCommand() + "] ";
        }
        return t + " ";
    }

    private static String fmt(double v) {
        long l = (long) v;
        return l == v ? String.valueOf(l) : String.valueOf(v);
    }

    /** 时间窗口校验：start/end 均为空 = 放行；解析失败 = 放行（不阻塞执行）。 */
    private boolean inClaimWindow(String start, String end) {
        if ((start == null || start.isEmpty()) && (end == null || end.isEmpty())) {
            return true;
        }
        long now = System.currentTimeMillis();
        try {
            if (start != null && !start.isEmpty()) {
                long s = parseTime(start);
                if (now < s) {
                    return false;
                }
            }
            if (end != null && !end.isEmpty()) {
                long e = parseTime(end);
                if (now > e) {
                    return false;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }

    private static long parseTime(String s) throws java.text.ParseException {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s).getTime();
        } catch (java.text.ParseException e) {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s).getTime();
        }
    }

    private boolean passCooldown(WebAction action, String playerName) {
        int cd = action.getCooldownSeconds();
        if (cd <= 0) {
            return true;
        }
        String key = playerName + "|" + action.getId();
        Long last = cooldowns.get(key);
        long now = System.currentTimeMillis();
        if (last != null && now - last < cd * 1000L) {
            return false;
        }
        cooldowns.put(key, now);
        return true;
    }
}
