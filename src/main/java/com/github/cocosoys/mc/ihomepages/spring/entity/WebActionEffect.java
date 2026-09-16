package com.github.cocosoys.mc.ihomepages.action.model;

/**
 * 单个效果定义（actions.yml 中 {@code effects:} 列表的一项）。
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
public class WebActionEffect {

    /** 效果类型：command / economy.take / economy.give。 */
    private String type;

    /** 指令模板（type=command 必填；type=economy.* 可选覆盖默认经济命令）。 */
    private String command;

    /** 强制命令类别：console / account / entity（可选，type=command 时生效）。 */
    private String target;

    /** 金额（economy.give 必填；economy.take 缺省取动作 price）。 */
    private double amount = 0D;

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
