package com.github.cocosoys.mc.ihomepages.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.ihomepages.action.model.ActionClaimRecord;
import com.github.cocosoys.mc.ihomepages.action.model.OfflineTask;
import com.github.cocosoys.mc.ihomepages.action.queue.ActionClaimStore;
import com.github.cocosoys.mc.ihomepages.action.queue.OfflineTaskQueue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 礼包链路 → 动作链路 一次性数据迁移器（插件启动时自动执行）。
 *
 * <p>转换规则：</p>
 * <ul>
 *   <li>{@code homepage_gift_pending} 中 status=pending 的记录：
 *       物品快照 items: [{material, amount}] → 每条 {@code give {player} <material> <amount>}；
 *       指令快照 commands: [cmd] → 每条原样。全部转为 {@code homepage_action_pending}（actionId=claim-gift），
 *       上线由动作链路统一补发；</li>
 *   <li>{@code homepage_gift_claim} 领取记录 → {@code homepage_action_claim}（actionId=claim-gift），
 *       保持周期去重语义；</li>
 *   <li>迁移成功的旧记录删除；旧数据本身不丢（已转换），若转换失败仅告警并保留原记录。</li>
 * </ul>
 *
 * <p>迁移目标动作 ID 固定 {@code claim-gift}（与 actions.yml 模板示例一致；服主若改名需同步改此处）。</p>
 */
public class GiftDataMigrator {

    /** 迁移写入的目标动作 ID。 */
    public static final String TARGET_ACTION = "claim-gift";

    private final JavaPlugin plugin;
    private final OfflineTaskQueue taskQueue;
    private final ActionClaimStore claimStore;

    public GiftDataMigrator(JavaPlugin plugin, OfflineTaskQueue taskQueue, ActionClaimStore claimStore) {
        this.plugin = plugin;
        this.taskQueue = taskQueue;
        this.claimStore = claimStore;
    }

    /** 执行迁移，返回迁移摘要（或空字符串表示无需迁移）。 */
    public String migrate() {
        int pending = migratePending();
        int claims = migrateClaims();
        if (pending + claims == 0) {
            return "";
        }
        return "礼包旧数据迁移完成：待补发 " + pending + " 条 → 动作队列，领取记录 " + claims + " 条 → 动作领取记录";
    }

    private int migratePending() {
        List<LegacyGiftPending> list;
        try {
            list = YAML.Pojo.select(LegacyGiftPending.class);
        } catch (Throwable t) {
            return 0; // 旧表不存在（全新安装）或读取失败
        }
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (LegacyGiftPending p : list) {
            if (p.getStatus() == null || !"pending".equals(p.getStatus())) {
                // 非 pending 残留（granted/failed）无补发价值，直接清理
                try {
                    YAML.Pojo.deleteById(LegacyGiftPending.class, p.getId());
                } catch (Throwable ignored) {
                }
                continue;
            }
            List<OfflineTask> converted = toOfflineTasks(p);
            if (converted.isEmpty()) {
                plugin.getLogger().warning("[迁移] 礼包待补发记录 " + p.getId() + " 无可转换内容，保留原记录");
                continue;
            }
            boolean ok = true;
            for (OfflineTask t : converted) {
                try {
                    YAML.Pojo.insert(t);
                } catch (RuntimeException e) {
                    ok = false;
                    plugin.getLogger().warning("[迁移] 离线任务写入失败: " + e.getMessage());
                }
            }
            if (ok) {
                try {
                    YAML.Pojo.deleteById(LegacyGiftPending.class, p.getId());
                    moved++;
                } catch (Throwable ignored) {
                }
            }
        }
        return moved;
    }

    /** 旧待补发记录 → 多条动作离线任务（物品→give 命令、指令原样；每条独立 UUID，一人可多条）。 */
    private List<OfflineTask> toOfflineTasks(LegacyGiftPending p) {
        List<OfflineTask> out = new ArrayList<>();
        String player = p.getPlayerName();
        String queuedAt = p.getClaimedAt() != null ? p.getClaimedAt() : String.valueOf(System.currentTimeMillis());
        // 物品 → give 命令
        String itemsSnap = p.getItemsSnapshot();
        if (itemsSnap != null && !itemsSnap.isEmpty()) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(new StringReader(itemsSnap));
                for (Map<?, ?> m : y.getMapList("items")) {
                    String mat = m.get("material") == null ? null : String.valueOf(m.get("material"));
                    int amount = (m.get("amount") instanceof Number) ? ((Number) m.get("amount")).intValue() : 1;
                    if (mat != null && !mat.trim().isEmpty()) {
                        out.add(new OfflineTask(UUID.randomUUID().toString(), player, p.getPlayerUuid(),
                                TARGET_ACTION, "give " + player + " " + mat.trim() + " " + Math.max(1, amount),
                                queuedAt, null, "pending"));
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[迁移] 物品快照解析失败: " + t.getMessage());
            }
        }
        // 指令原样
        String cmdSnap = p.getCommandsSnapshot();
        if (cmdSnap != null && !cmdSnap.isEmpty()) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(new StringReader(cmdSnap));
                for (String cmd : y.getStringList("commands")) {
                    if (cmd != null && !cmd.trim().isEmpty()) {
                        out.add(new OfflineTask(UUID.randomUUID().toString(), player, p.getPlayerUuid(),
                                TARGET_ACTION, cmd.replace("{player}", player),
                                queuedAt, null, "pending"));
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[迁移] 指令快照解析失败: " + t.getMessage());
            }
        }
        return out;
    }

    private int migrateClaims() {
        List<LegacyGiftClaim> list;
        try {
            list = YAML.Pojo.select(LegacyGiftClaim.class);
        } catch (Throwable t) {
            return 0;
        }
        if (list == null || list.isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (LegacyGiftClaim c : list) {
            if (c.getPlayerName() == null || c.getPlayerName().isEmpty()) {
                try {
                    YAML.Pojo.deleteById(LegacyGiftClaim.class, c.getPlayerUuid());
                } catch (Throwable ignored) {
                }
                continue;
            }
            try {
                YAML.Pojo.insert(new ActionClaimRecord(
                        UUID.randomUUID().toString(), c.getPlayerName(), TARGET_ACTION,
                        c.getClaimedAt() != null ? c.getClaimedAt() : String.valueOf(System.currentTimeMillis()),
                        c.getPeriod() != null ? c.getPeriod() : "once"));
                YAML.Pojo.deleteById(LegacyGiftClaim.class, c.getPlayerUuid());
                moved++;
            } catch (Throwable t) {
                plugin.getLogger().warning("[迁移] 领取记录转换失败: " + t.getMessage());
            }
        }
        return moved;
    }
}
