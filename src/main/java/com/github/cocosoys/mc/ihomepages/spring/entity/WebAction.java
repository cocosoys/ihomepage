package com.github.cocosoys.mc.ihomepages.spring.entity;

import com.dlz.db.annotation.TableField;
import com.dlz.db.annotation.TableId;
import com.dlz.db.annotation.TableName;

import java.util.ArrayList;
import java.util.List;

/**
 * 网页动作定义（对应 actions.yml 中 {@code actions.<id>} 的一项）。
 *
 * <p>ORM 实体：主表 {@code homepage_action}，由 {@code WebActionManager} 在启动/重载时将
 * actions.yml（服主的简易添加方式）同步落库；运行期从数据库读取。效果列表存子表
 * {@code homepage_action_effect}（{@link WebActionEffect#getActionId()} 关联），
 * {@link #getEffects()} 由 Manager 加载后组装，不直接落库。</p>
 *
 * <p>执行语义：按 {@code effects} 列表顺序逐个执行效果（可编排「先扣款、再发物、再给权限」等）。</p>
 */
@TableName("homepage_action")
public class WebAction {

    /** 动作 ID：唯一标识（主键），前端按钮与代码调用均引用它（小写字母/数字/下划线/连字符，≤32）。 */
    @TableId
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

    /** 效果列表（按顺序执行；存子表 homepage_action_effect，由 Manager 组装，不直接落库）。 */
    @TableField(exist = false)
    private List<WebActionEffect> effects = new ArrayList<>();

    /** 前端展示配置（内存对象；落库时拆分为下方 display* 5 列）。 */
    @TableField(exist = false)
    private DisplayConfig display;

    // ===== display 拆列（ORM 落库字段；由 Manager 在读写时与 display 对象互转） =====

    /** display.icon（拆列）。 */
    private String displayIcon = "";

    /** display.desc（拆列）。 */
    private String displayDesc = "";

    /** display.group（拆列）。 */
    private String displayGroup = "";

    /** display.sort（拆列）。 */
    private int displaySort = 0;

    /** display.hidden（拆列）。 */
    private boolean displayHidden = false;

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

    // ===== display 拆列互转（ORM 读写时由 Manager 调用） =====

    /** 用 display 对象填充拆列字段（写库前调用；display=null 时清为默认值）。 */
    public void extractDisplayColumns() {
        if (display == null) {
            displayIcon = "";
            displayDesc = "";
            displayGroup = "";
            displaySort = 0;
            displayHidden = false;
            return;
        }
        displayIcon = display.getIcon();
        displayDesc = display.getDesc();
        displayGroup = display.getGroup();
        displaySort = display.getSort();
        displayHidden = display.isHidden();
    }

    /** 用拆列字段组装 display 对象（读库后调用；5 列全默认视为未配置，返回 null）。 */
    public void applyDisplayColumns() {
        boolean blank = (displayIcon == null || displayIcon.isEmpty())
                && (displayDesc == null || displayDesc.isEmpty())
                && (displayGroup == null || displayGroup.isEmpty())
                && displaySort == 0 && !displayHidden;
        if (blank) {
            display = null;
            return;
        }
        DisplayConfig d = new DisplayConfig();
        d.setIcon(displayIcon == null ? "" : displayIcon);
        d.setDesc(displayDesc == null ? "" : displayDesc);
        d.setGroup(displayGroup == null ? "" : displayGroup);
        d.setSort(displaySort);
        d.setHidden(displayHidden);
        display = d;
    }

    public String getDisplayIcon() {
        return displayIcon;
    }

    public void setDisplayIcon(String displayIcon) {
        this.displayIcon = displayIcon;
    }

    public String getDisplayDesc() {
        return displayDesc;
    }

    public void setDisplayDesc(String displayDesc) {
        this.displayDesc = displayDesc;
    }

    public String getDisplayGroup() {
        return displayGroup;
    }

    public void setDisplayGroup(String displayGroup) {
        this.displayGroup = displayGroup;
    }

    public int getDisplaySort() {
        return displaySort;
    }

    public void setDisplaySort(int displaySort) {
        this.displaySort = displaySort;
    }

    public boolean isDisplayHidden() {
        return displayHidden;
    }

    public void setDisplayHidden(boolean displayHidden) {
        this.displayHidden = displayHidden;
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
