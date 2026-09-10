package com.github.cocosoys.mc.ihomepages.migration;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

/**
 * 旧礼包领取记录（表 homepage_gift_claim）的「只读迁移结构」。
 *
 * <p>仅用于 {@link GiftDataMigrator} 一次性读取旧数据并转换为动作链路领取记录（homepage_action_claim），
 * 迁移完成后本类无运行时用途。</p>
 */
@TableName("homepage_gift_claim")
public class LegacyGiftClaim {

    @TableId
    private String playerUuid;
    private String playerName;
    private String claimedAt;
    private String period;

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
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
