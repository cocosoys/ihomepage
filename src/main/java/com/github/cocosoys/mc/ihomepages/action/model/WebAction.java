package com.github.cocosoys.mc.ihomepages.action.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 网页动作定义（对应 actions.yml 中 {@code actions.<id>} 的一项）。
 *
 * <p>由服主在配置文件声明，运行时由 {@code WebActionManager} 加载校验。
 * 执行语义：按 {@code effects} 列表顺序逐个执行效果（可编排「先扣款、再发物、再给权限」等）。</p>
 */
public class WebAction {

    /** 动作 ID：唯一标识，前端按钮与代码调用均引用它（小写字母/数字/下划线/连字符，≤32）。 */
    private String id;

    /** 动作显示名（前端按钮/卡片标题）。 */
    private String name;

    /** 价格（服务端扣款额，Vault/经济命令）；0 = 免费。 */
    private double price = 0D;

    /** 离线策略：run（离线也立即执行）/ queue（离线入队，上线补执行）/ reject（离线拒绝）。默认 queue。 */
    private String offline = "queue";

    /** 冷却秒数：同一玩家两次执行的最小间隔。0 = 不限。 */
    private int cooldownSeconds = 0;

    /** 扣款前是否预检余额（需要 Vault，未装则跳过预检）。 */
    private boolean balanceCheck = false;

    /** 领取周期去重：once / daily / weekly；空 = 不限（每次点击均可执行，仅受冷却约束）。 */
    private String claimPeriod;

    /** 领取时间窗口开始（yyyy-MM-dd HH:mm:ss）；空 = 不限制。 */
    private String claimWindowStart;

    /** 领取时间窗口结束（yyyy-MM-dd HH:mm:ss）；空 = 不限制。 */
    private String claimWindowEnd;

    /** 效果列表（按顺序执行）。 */
    private List<WebActionEffect> effects = new ArrayList<>();

    /** 前端展示配置（可选；无则自动隐藏展示属性）。 */
    private DisplayConfig display;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getOffline() {
        return offline;
    }

    public void setOffline(String offline) {
        this.offline = offline;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public boolean isBalanceCheck() {
        return balanceCheck;
    }

    public void setBalanceCheck(boolean balanceCheck) {
        this.balanceCheck = balanceCheck;
    }

    public String getClaimPeriod() {
        return claimPeriod;
    }

    public void setClaimPeriod(String claimPeriod) {
        this.claimPeriod = claimPeriod;
    }

    public String getClaimWindowStart() {
        return claimWindowStart;
    }

    public void setClaimWindowStart(String claimWindowStart) {
        this.claimWindowStart = claimWindowStart;
    }

    public String getClaimWindowEnd() {
        return claimWindowEnd;
    }

    public void setClaimWindowEnd(String claimWindowEnd) {
        this.claimWindowEnd = claimWindowEnd;
    }

    /** 是否领取型动作（配置了 claim-period）。 */
    public boolean isClaimAction() {
        return claimPeriod != null && !claimPeriod.trim().isEmpty();
    }

    public List<WebActionEffect> getEffects() {
        return effects;
    }

    public void setEffects(List<WebActionEffect> effects) {
        this.effects = effects;
    }

    public DisplayConfig getDisplay() {
        return display;
    }

    public void setDisplay(DisplayConfig display) {
        this.display = display;
    }

    /** 是否在前端展示（display.hidden != true 且已配置 display 或默认可见）。 */
    public boolean isVisible() {
        return display == null || !display.isHidden();
    }

    /** 前端展示配置（可选字段；display.hidden=true 时仅代码调用，不出现在前端列表）。 */
    public static class DisplayConfig {
        private String icon = "";
        private String desc = "";
        private String group = "";
        private int sort = 0;
        private boolean hidden = false;

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public int getSort() {
            return sort;
        }

        public void setSort(int sort) {
            this.sort = sort;
        }

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }
    }
}
