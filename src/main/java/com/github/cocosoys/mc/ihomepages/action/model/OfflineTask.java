package com.github.cocosoys.mc.ihomepages.action.model;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

/**
 * 离线待执行任务（ORM 实体，落 {@code data/homepage_action_pending.yml} / SQL 表 homepage_action_pending）。
 *
 * <p>当动作的某个命令效果属「实体类」（如 give）且玩家离线、动作 offline 策略为 {@code queue} 时，
 * 写入本队列；玩家上线（PlayerJoinEvent）后由 {@code OfflineTaskQueue.deliver} 逐条按命令快照补执行。</p>
 *
 * <p>主键为 UUID，同一玩家可同时持有多条任务（多个动作 / 多次领取），互不覆盖；
 * 命令快照独立于 actions.yml，后续服主改配置不影响已入队任务。</p>
 */
@TableName("homepage_action_pending")
public class OfflineTask {

    /** 主键：UUID，每条任务全局唯一（支持一人多条）。 */
    @TableId
    private String id;

    /** 任务归属玩家名（补发按此查询）。 */
    private String playerName;

    /** 玩家 UUID（审计用途，不作匹配键）。 */
    private String playerUuid;

    /** 触发任务的动作 ID。 */
    private String actionId;

    /** 待执行命令快照（入队时已填充 {player}/{amount}，为最终命令；独立于 actions.yml 后续修改）。 */
    private String command;

    /** 入队时刻（epoch 毫秒字符串）。 */
    private String queuedAt;

    /** 补发完成 / 失败时刻（epoch 毫秒字符串）。 */
    private String doneAt;

    /** 状态：pending（待补发）/ granted（已补发）/ failed（补发失败）。 */
    private String status = "pending";

    public OfflineTask() {
    }

    public OfflineTask(String id, String playerName, String playerUuid, String actionId,
                       String command, String queuedAt, String doneAt, String status) {
        this.id = id;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.actionId = actionId;
        this.command = command;
        this.queuedAt = queuedAt;
        this.doneAt = doneAt;
        this.status = status;
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

    public String getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(String queuedAt) {
        this.queuedAt = queuedAt;
    }

    public String getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(String doneAt) {
        this.doneAt = doneAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
