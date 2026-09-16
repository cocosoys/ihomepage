package com.github.cocosoys.mc.ihomepages.spring.entity;

import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

/**
 * 单个效果定义（actions.yml 中 {@code effects:} 列表的一项；ORM 子表 {@code homepage_action_effect}）。
 *
 * <p>子表经 {@link #getActionId()} 关联主表 {@code homepage_action}，{@code sort} 保存列表顺序。
 * 由 {@code WebActionManager} 在同步存储时写入、加载时按 actionId 组装回 {@link WebAction#getEffects()}。</p>
 *
 * <p>支持类型：</p>
 * <ul>
 *   <li>{@code command} —— 执行服务器指令；{@code command} 模板支持 {@code {player}} / {@code {amount}} 占位符；
 *       {@code target} 可强制指定类别（console/account/entity），不填由 CommandClassifier 自动识别；</li>
 *   <li>{@code economy.take} —— 扣除货币（走经济命令，默认 {@code eco take {player} {amount}}，
 *       可用 {@code command} 覆盖为 CMI 等经济插件命令）；{@code amount} 缺省取动作 price；</li>
 *   <li>{@code economy.give} —— 增加货币（默认 {@code eco give {player} {amount}}），{@code amount} 必填。</li>
 * </ul>
 */
@TableName("homepage_action_effect")
public class WebActionEffect {

    /** 主键：UUID，每条效果记录全局唯一。 */
    @TableId
    private String id;

    /** 所属动作 ID（关联主表 homepage_action.id）。 */
    private String actionId;

    /** 效果在动作内的顺序（0 起，按执行序）。 */
    private int sort = 0;

    /** 效果类型：command / economy.take / economy.give。 */
    private String type;

    /** 指令模板（type=command 必填；type=economy.* 可选覆盖默认经济命令）。 */
    private String command;

    /** 强制命令类别：console / account / entity（可选，type=command 时生效）。 */
    private String target;

    /** 金额（economy.give 必填；economy.take 缺省取动作 price）。 */
    private double amount = 0D;

    public WebActionEffect() {
    }

    public WebActionEffect(String id, String actionId, int sort) {
        this.id = id;
        this.actionId = actionId;
        this.sort = sort;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }

    public int getSort() {
        return sort;
    }

    public void setSort(int sort) {
        this.sort = sort;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
