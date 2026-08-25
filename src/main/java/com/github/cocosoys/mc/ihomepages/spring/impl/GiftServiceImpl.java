package com.github.cocosoys.mc.ihomepages.spring.impl;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.i18n.I18n;
import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;
import com.github.cocosoys.mc.ihomepages.config.HomeConfigEntity;
import com.github.cocosoys.mc.ihomepages.config.IHomeConfigSource;
import com.github.cocosoys.mc.ihomepages.spring.entity.GiftClaimRecord;
import com.github.cocosoys.mc.ihomepages.spring.entity.GiftCommand;
import com.github.cocosoys.mc.ihomepages.spring.entity.GiftConfigEntity;
import com.github.cocosoys.mc.ihomepages.spring.entity.GiftItem;
import com.github.cocosoys.mc.ihomepages.spring.service.IConfigReader;
import com.github.cocosoys.mc.ihomepages.spring.service.IGiftService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 礼包领取服务实现：依据逻辑配置（enabled）与内容配置（home.yml 的 gift 段）处理领取。
 * <ul>
 *   <li>未登录玩家 → 401；功能禁用 / 无礼包 → 403；已领取 → 409；</li>
 *   <li>领取记录落 YAML ORM（{@code data/homepage_gift_claim.yml}）。</li>
 *   <li>支持时间窗口校验（{@code claim-limit.start/end}，格式 yyyy-MM-dd HH:mm:ss）。</li>
 * </ul>
 */
public class GiftServiceImpl implements IGiftService {

    /** 时间格式：到秒 */
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final IConfigReader config;
    private final IHomeConfigSource home;
    private final JavaPlugin plugin;

    /** 按玩家 UUID 的细粒度锁，串行化同一玩家的「检查→发放→落记录」，杜绝并发重复领取。 */
    private final ConcurrentHashMap<String, Object> claimLocks = new ConcurrentHashMap<>();

    public GiftServiceImpl(IConfigReader config, IHomeConfigSource home, JavaPlugin plugin) {
        this.config = config;
        this.home = home;
        this.plugin = plugin;
    }

    @Override
    public AjaxResult claim(Player player) {
        if (player == null) {
            return AjaxResult.unauthorizedT("gift.claim.unauthorized", "请先进入游戏并登录后再领取礼包");
        }
        if (!config.isEnabled()) {
            return AjaxResult.errorT(403, "gift.claim.disabled", "自定义主页功能未启用");
        }
        HomeConfigEntity hc = home.get();
        if (hc == null) {
            return AjaxResult.errorT(500, "gift.claim.no-config", "主页配置未加载");
        }
        GiftConfigEntity gift = hc.getGift();
        if (gift == null || !gift.isEnabled()) {
            return AjaxResult.errorT(403, "gift.claim.no-gift", "当前没有可领取的礼包");
        }
        // 时间窗口校验（只读配置，可在任意线程执行）
        if (!isInTimeWindow(gift.getPeriodStart(), gift.getPeriodEnd())) {
            return AjaxResult.errorT(403, "gift.claim.out-of-window", "当前不在礼包领取时间范围内");
        }

        // 同一玩家并发领取请求在此锁上串行，保证 check→grant→record 原子
        final String uuid = player.getUniqueId().toString();
        Object lock = claimLocks.computeIfAbsent(uuid, k -> new Object());
        try {
            synchronized (lock) {
                AtomicReference<AjaxResult> result = new AtomicReference<>();
                runOnPrimaryThread(player, () -> result.set(doClaim(player, gift)));
                return result.get();
            }
        } finally {
            claimLocks.remove(uuid, lock);
        }
    }

    /**
     * 主线程原子执行的领取动作（重复检查已被外层 per-player 锁排除，此处仅需保证在主线程）。
     */
    private AjaxResult doClaim(Player player, GiftConfigEntity gift) {
        String uuid = player.getUniqueId().toString();
        String mode = gift.getPeriodMode();
        GiftClaimRecord rec = YAML.Pojo.get(GiftClaimRecord.class, uuid);
        if (rec != null && alreadyClaimed(rec, mode)) {
            return AjaxResult.errorT(409, "gift.claim.already-claimed", "你已经领取过礼包啦");
        }
        // 发放物品 + 执行控制台指令
        grant(player, gift);
        // 落领取记录
        GiftClaimRecord nr = new GiftClaimRecord(uuid, player.getName(),
                String.valueOf(System.currentTimeMillis()), mode);
        YAML.Pojo.insert(nr);
        return AjaxResult.successT("gift.claim.success", "领取成功，礼包已发放到你的背包");
    }

    /**
     * 确保 Bukkit 相关操作（addItem / dispatchCommand）运行在主线程：
     * 已主线程则直接执行；否则调度到主线程并阻塞等待其完成，以保持「同步领取」语义与锁覆盖。
     */
    private void runOnPrimaryThread(Player player, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public AjaxResult status(Player player) {
        if (player == null) {
            return AjaxResult.unauthorizedT("gift.status.unauthorized", "请先进入游戏");
        }
        if (!config.isEnabled()) {
            return AjaxResult.errorT(403, "gift.status.disabled", "自定义主页功能未启用");
        }
        HomeConfigEntity hc = home.get();
        if (hc == null) {
            return AjaxResult.errorT(500, "gift.status.no-config", "主页配置未加载");
        }
        GiftConfigEntity gift = hc.getGift();
        if (gift == null || !gift.isEnabled()) {
            return AjaxResult.errorT(403, "gift.status.no-gift", "当前没有可领取的礼包");
        }

        String uuid = player.getUniqueId().toString();
        GiftClaimRecord rec = YAML.Pojo.get(GiftClaimRecord.class, uuid);
        boolean claimed = (rec != null);
        String claimedAt = claimed ? rec.getClaimedAt() : null;
        boolean inWindow = isInTimeWindow(gift.getPeriodStart(), gift.getPeriodEnd());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("claimed", claimed);
        data.put("period", gift.getPeriodMode());
        data.put("claimedAt", claimedAt);
        data.put("canClaim", !claimed && inWindow);
        data.put("periodStart", gift.getPeriodStart());
        data.put("periodEnd", gift.getPeriodEnd());

        return AjaxResult.success(data);
    }

    // ==================== 时间窗口 ====================

    /**
     * 判断当前时间是否在指定的时间窗口内。
     *
     * @param start 窗口开始时间（格式 yyyy-MM-dd HH:mm:ss），空字符串表示不限制
     * @param end   窗口结束时间（格式 yyyy-MM-dd HH:mm:ss），空字符串表示不限制
     * @return true 表示当前时间在窗口内（或窗口未设置）
     */
    private boolean isInTimeWindow(String start, String end) {
        if ((start == null || start.isEmpty()) && (end == null || end.isEmpty())) {
            return true; // 未设置时间窗口，放行
        }
        long now = System.currentTimeMillis();
        try {
            if (start != null && !start.isEmpty()) {
                long startMs = parseTime(start);
                if (now < startMs) {
                    return false;
                }
            }
            if (end != null && !end.isEmpty()) {
                long endMs = parseTime(end);
                if (now > endMs) {
                    return false;
                }
            }
        } catch (Exception e) {
            // 解析失败则放行，不阻塞领取
            return true;
        }
        return true;
    }

    /**
     * 将时间字符串解析为毫秒时间戳。
     * 优先尝试 {@code yyyy-MM-dd HH:mm:ss} 格式，失败则尝试 {@code yyyy-MM-dd} 格式。
     */
    private long parseTime(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
            return sdf.parse(timeStr).getTime();
        } catch (Exception e) {
            // 尝试仅日期格式
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                return sdf.parse(timeStr).getTime();
            } catch (Exception e2) {
                throw new IllegalArgumentException(I18n.t("exception.gift.time-parse-fail", "无法解析时间字符串: {0}", timeStr));
            }
        }
    }

    // ==================== 领取去重 ====================

    private boolean alreadyClaimed(GiftClaimRecord rec, String mode) {
        if (!mode.equals(rec.getPeriod())) {
            return false; // 周期模式变化视为新的一轮
        }
        long claimed = parseLong(rec.getClaimedAt());
        if (claimed <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        if ("daily".equals(mode)) {
            return sameDay(claimed, now);
        } else if ("weekly".equals(mode)) {
            return sameWeek(claimed, now);
        }
        return true; // once
    }

    // ==================== 发放 ====================

    private void grant(Player p, GiftConfigEntity g) {
        if (g.getItems() != null) {
            for (GiftItem it : g.getItems()) {
                Material m = Material.matchMaterial(it.getMaterial());
                if (m != null && m != Material.AIR) {
                    p.getInventory().addItem(new ItemStack(m, Math.max(1, it.getAmount())));
                }
            }
        }
        if (g.getCommands() != null) {
            for (GiftCommand c : g.getCommands()) {
                String cmd = c.getCmd().replace("{player}", p.getName());
                if (!cmd.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
        }
    }

    // ==================== 工具 ====================

    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static boolean sameWeek(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.WEEK_OF_YEAR) == cb.get(Calendar.WEEK_OF_YEAR);
    }
}