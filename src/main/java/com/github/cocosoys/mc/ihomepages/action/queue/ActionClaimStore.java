package com.github.cocosoys.mc.ihomepages.action.queue;

import com.github.cocosoys.mc.soyshttpovermc.orm.YAML;
import com.github.cocosoys.mc.ihomepages.action.model.ActionClaimRecord;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * 领取型动作的去重存储：查询 / 判定 / 记录（落 {@code homepage_action_claim}）。
 *
 * <p>周期语义与礼包链路保持一致：once=领取过即不可再领；daily=同一天不可再领；weekly=同一周不可再领；
 * 周期模式变化（配置调整）视为新一轮，允许重新领取。查询以 {@code playerName + actionId} 为键。</p>
 */
public class ActionClaimStore {

    /** 查询玩家在指定动作上的最新领取记录（无则 null；忽略大小写防会话名不一致）。 */
    public ActionClaimRecord findLatest(String playerName, String actionId) {
        if (playerName == null || actionId == null) {
            return null;
        }
        try {
            List<ActionClaimRecord> list = YAML.Pojo.select(ActionClaimRecord.class);
            if (list != null) {
                ActionClaimRecord best = null;
                for (ActionClaimRecord r : list) {
                    if (r.getPlayerName() != null && r.getPlayerName().equalsIgnoreCase(playerName)
                            && actionId.equals(r.getActionId())) {
                        if (best == null || compare(r.getClaimedAt(), best.getClaimedAt()) > 0) {
                            best = r;
                        }
                    }
                }
                return best;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * 周期内是否已领取。
     *
     * @param rec    领取记录（null = 从未领取）
     * @param period 当前配置的周期模式（once/daily/weekly）
     * @param now    当前时刻（ms）
     */
    public boolean isClaimed(ActionClaimRecord rec, String period, long now) {
        if (rec == null) {
            return false;
        }
        if (period == null || !period.equals(rec.getPeriod())) {
            return false; // 周期模式变化（如 once→daily）视为新一轮
        }
        long claimed = parseLong(rec.getClaimedAt());
        if (claimed <= 0) {
            return true;
        }
        if ("daily".equals(period)) {
            return sameDay(claimed, now);
        }
        if ("weekly".equals(period)) {
            return sameWeek(claimed, now);
        }
        return true; // once
    }

    /** 落一条领取记录（执行成功后调用）。 */
    public void record(String playerName, String actionId, String period) {
        try {
            YAML.Pojo.insert(new ActionClaimRecord(
                    UUID.randomUUID().toString(), playerName, actionId,
                    String.valueOf(System.currentTimeMillis()), period));
        } catch (RuntimeException e) {
            // 记录失败不阻断业务（仅影响去重精度，冷却仍生效）
        }
    }

    private static int compare(String a, String b) {
        long la = parseLong(a);
        long lb = parseLong(b);
        return Long.compare(la, lb);
    }

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
