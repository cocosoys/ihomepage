package com.github.cocosoys.mc.ihomepages.migration;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

/**
 * 旧礼包离线待发放记录（表 homepage_gift_pending）的「只读迁移结构」。
 *
 * <p>仅用于 {@link GiftDataMigrator} 一次性读取旧数据并转换为动作链路（homepage_action_pending），
 * 迁移完成后本类无运行时用途。</p>
 */
@TableName("homepage_gift_pending")
public class LegacyGiftPending {

    @TableId
    private String id;
    private String playerName;
    private String playerUuid;
    private String giftId;
    private String itemsSnapshot;
    private String commandsSnapshot;
    private String claimedAt;
    private String grantedAt;
    private String status;

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

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getGiftId() {
        return giftId;
    }

    public void setGiftId(String giftId) {
        this.giftId = giftId;
    }

    public String getItemsSnapshot() {
        return itemsSnapshot;
    }

    public void setItemsSnapshot(String itemsSnapshot) {
        this.itemsSnapshot = itemsSnapshot;
    }

    public String getCommandsSnapshot() {
        return commandsSnapshot;
    }

    public void setCommandsSnapshot(String commandsSnapshot) {
        this.commandsSnapshot = commandsSnapshot;
    }

    public String getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(String claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(String grantedAt) {
        this.grantedAt = grantedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
