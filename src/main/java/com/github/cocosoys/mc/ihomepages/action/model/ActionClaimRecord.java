package com.github.cocosoys.mc.ihomepages.action.model;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

/**
 * 领取型动作的领取记录（ORM 实体，落 {@code data/homepage_action_claim.yml} / SQL 表 homepage_action_claim）。
 *
 * <p>当动作配置了 {@code claim-period}（once/daily/weekly）时，每次成功执行（含离线入队）落一条记录，
 * 用于跨重启持久去重——玩家不可在周期内重复领取。查询以 {@code playerName + actionId} 为键（主键 id 仅保证唯一）。</p>
 */
@TableName("homepage_action_claim")
public class ActionClaimRecord {

    /** 主键：UUID，每条领取记录全局唯一。 */
    @TableId
    private String id;

    /** 领取主体：玩家名（去重查询键之一）。 */
    private String playerName;

    /** 动作 ID（去重查询键之一）。 */
    private String actionId;

    /** 领取时刻（epoch 毫秒字符串）。 */
    private String claimedAt;

    /** 领取时的周期模式：once / daily / weekly（周期模式变化视为新一轮）。 */
    private String period;

    public ActionClaimRecord() {
    }

    public ActionClaimRecord(String id, String playerName, String actionId, String claimedAt, String period) {
        this.id = id;
        this.playerName = playerName;
        this.actionId = actionId;
        this.claimedAt = claimedAt;
        this.period = period;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(String claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }
}
